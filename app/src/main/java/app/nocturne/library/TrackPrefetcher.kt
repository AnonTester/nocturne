package app.nocturne.library

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import app.nocturne.data.Source
import app.nocturne.data.TrackDao
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private const val TAG = "NocturneSMB"

/**
 * Reads whole Samba files into [TrackRamCache] in the background: the playing track first, then the next one. Uses its
 * own connection set, so a download can never stall playback. A new request (skip, other queue) replaces the running one.
 */
@UnstableApi
class TrackPrefetcher(
    private val dao: TrackDao,
    private val providers: SmbProviders,
    private val cache: TrackRamCache,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    /** [ids]: the playing track first, then what plays next. */
    @Synchronized fun request(ids: List<String>) {
        job?.cancel()
        if (!cache.enabled || ids.isEmpty()) return
        job = scope.launch(Dispatchers.IO) { fetchAll(ids) }
    }

    private suspend fun fetchAll(ids: List<String>) {
        val tracks = ids.mapNotNull { dao.byId(it) }
        val current = tracks.firstOrNull()?.let(TrackRamCache::keyFor)
        for (t in tracks) {
            if (t.source == Source.LOCAL) continue
            val key = TrackRamCache.keyFor(t)
            if (cache.contains(key) || (t.size > 0 && !cache.fits(t.size))) continue
            try {
                fetch(Uri.parse(t.uri), key, current, t.filename)
            } catch (e: IOException) {
                Log.w(TAG, "prefetch ${t.filename}: ${e.message}")
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "prefetch ${t.filename}: out of memory")
                return
            }
        }
    }

    private suspend fun fetch(uri: Uri, key: String, current: String?, name: String) {
        val t0 = System.nanoTime()
        val source = SmbDataSource(providers.get(SmbUri.sourceOf(uri)))
        try {
            val size = source.open(DataSpec(uri))
            if (size == C.LENGTH_UNSET.toLong() || !cache.fits(size)) return
            val data = ByteBuffer.allocateDirect(size.toInt())
            val chunk = ByteArray(CHUNK)
            while (data.hasRemaining()) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val n = source.read(chunk, 0, minOf(CHUNK, data.remaining()))
                if (n <= 0) return // short file: never store a partial one
                data.put(chunk, 0, n)
            }
            data.flip()
            if (cache.put(key, data, current)) {
                Log.d(TAG, "prefetched $name ${size / 1024} KiB in ${(System.nanoTime() - t0) / 1_000_000}ms (cache ${cache.bytes() / 1024} KiB)")
            }
        } finally {
            source.close()
        }
    }

    private companion object {
        const val CHUNK = 256 * 1024
    }
}
