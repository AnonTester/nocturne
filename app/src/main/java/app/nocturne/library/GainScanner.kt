package app.nocturne.library

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.media3.common.util.UnstableApi
import app.nocturne.data.GainSource
import app.nocturne.data.SettingsRepository
import app.nocturne.data.Track
import app.nocturne.data.TrackDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Finds the loudness of every track in the background: ReplayGain tag first (cheap), a measurement of the middle of the
 * song when there is no tag. Results are cached in the database. The playback engine asks for the current and upcoming
 * tracks first ([request]); everything else follows in folder order. Runs one track at a time on its own connections
 * so it can never starve playback, and waits while a track is loading.
 */
@UnstableApi
class GainScanner(
    context: Context,
    private val dao: TrackDao,
    private val settings: SettingsRepository,
    smb: SmbProviders,
    private val scope: CoroutineScope,
    private val isPlayerBusy: () -> Boolean,
) {
    private val tags = ReplayGainReader(context.applicationContext, smb)
    private val meter = LoudnessMeter(context.applicationContext, smb)
    private val lock = Any()
    private var wanted: List<String> = emptyList()

    fun start() {
        scope.launch(Dispatchers.Default) { loop() }
    }

    /** Replaces the list of tracks to do first (current track, then what is about to play). */
    fun request(ids: List<String>) = synchronized(lock) { wanted = ids }

    private suspend fun loop() {
        while (scope.isActive) {
            try {
                if (!settings.flow.first().gainScan) { delay(IDLE_MS); continue }
                if (isPlayerBusy()) { delay(1_000); continue }
                val n = next()
                if (n == null) { delay(IDLE_MS); continue }
                val (track, job) = n
                process(track, job)
                if (job == Job.MEASURE_ONLY) delay(PAUSE_BETWEEN_MS) // keep the CPU calm during the long measuring run
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "gain loop: ${e.message}")
                delay(IDLE_MS)
            }
        }
    }

    private fun takeWanted(): String? = synchronized(lock) {
        val id = wanted.firstOrNull()
        if (id != null) wanted = wanted.drop(1)
        id
    }

    /** What to do with a track: read its tag, measure it, or both in turn (urgent requests). */
    private enum class Job { TAG_ONLY, MEASURE_ONLY, BOTH }

    private suspend fun next(): Pair<Track, Job>? {
        while (true) {
            val id = takeWanted() ?: break
            val t = dao.byId(id) ?: continue
            when (t.gainSource) {
                GainSource.UNKNOWN -> return t to Job.BOTH
                GainSource.UNTAGGED -> return t to Job.MEASURE_ONLY
            }
        }
        // Background order: tags of the whole library first (cheap), then the measurements of what has no tag.
        dao.pendingGain(GainSource.UNKNOWN, 1).firstOrNull()?.let { return it to Job.TAG_ONLY }
        return dao.pendingGain(GainSource.UNTAGGED, 1).firstOrNull()?.let { it to Job.MEASURE_ONLY }
    }

    private suspend fun process(t: Track, job: Job) {
        try {
            if (job != Job.MEASURE_ONLY) {
                val tag = try { tags.read(t) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                if (tag != null) {
                    Log.d(TAG, "tag ${t.source} ${tag.gainDb} dB: ${t.filename}")
                    dao.setGain(t.id, tag.gainDb, tag.peak, GainSource.TAG)
                    return
                }
                if (job == Job.TAG_ONLY) {
                    Log.d(TAG, "untagged ${t.source}: ${t.filename}")
                    dao.setGain(t.id, null, null, GainSource.UNTAGGED)
                    return
                }
            }
            val m = withContext(Dispatchers.IO) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                meter.measure(t)
            }
            Log.d(TAG, "measured ${t.source} ${m.gainDb} dB (${"%.1f".format(m.lufs)} LUFS, peak ${m.peak}): ${t.filename}")
            dao.setGain(t.id, m.gainDb, m.peak, GainSource.MEASURED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "no loudness for ${t.filename}: ${e.message}")
            dao.setGain(t.id, null, null, GainSource.FAILED)
        }
    }

    private companion object {
        const val TAG = "NocturneGain"
        const val IDLE_MS = 5_000L
        const val PAUSE_BETWEEN_MS = 150L
    }
}
