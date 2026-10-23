package app.nocturne.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import app.nocturne.data.Source
import app.nocturne.data.Track
import app.nocturne.data.TrackDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Reads title/artist/album/duration from the files themselves, in the background, a batch at a time. */
class MetadataEnricher(
    private val context: Context,
    private val dao: TrackDao,
    private val smb: SmbProviders,
) {
    /** Returns how many tracks were processed in this pass (0 means nothing is pending). */
    suspend fun runBatch(batchSize: Int = 120): Int {
        val batch = dao.pendingMetadata(batchSize)
        if (batch.isEmpty()) return 0
        val gate = Semaphore(3)
        coroutineScope {
            batch.map { t -> async(Dispatchers.IO) { gate.withPermit { enrich(t) } } }.awaitAll()
        }
        return batch.size
    }

    private suspend fun enrich(t: Track) {
        val r = MediaMetadataRetriever()
        var smbSource: SmbMediaDataSource? = null
        try {
            if (Source.isSmb(t.source)) {
                smbSource = SmbMediaDataSource(smb.get(t.source), SmbUri.sharePath(Uri.parse(t.uri)))
                r.setDataSource(smbSource)
            } else {
                r.setDataSource(context, Uri.parse(t.uri))
            }
            fun tag(key: Int) = r.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            val artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            dao.updateMetadata(
                id = t.id,
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: t.title,
                artist = artist ?: t.artist,
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: t.album,
                durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                hasCover = false,
            )
        } catch (e: Exception) {
            // Unreadable/odd file: keep the filename-based info and don't retry forever.
            dao.markMetaDone(t.id)
        } finally {
            runCatching { r.release() }
            runCatching { smbSource?.close() }
        }
    }
}
