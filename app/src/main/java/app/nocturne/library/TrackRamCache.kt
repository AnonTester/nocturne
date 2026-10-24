package app.nocturne.library

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import app.nocturne.data.Track
import java.nio.ByteBuffer

/**
 * Whole Samba files held in memory (the previous, current and next track), so seeking and track changes need no network
 * and never wait for the NAS disk. Direct buffers keep them off the Java heap. Only complete files are stored.
 */
class TrackRamCache(
    private val maxFileBytes: Long = MAX_FILE_BYTES,
    private val budgetBytes: Long = BUDGET_BYTES,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private val entries = LinkedHashMap<String, ByteBuffer>(8, 0.75f, true) // access order: least recently used first

    /** Off = nothing is stored or served. */
    @Volatile var enabled = true
        set(value) { field = value; if (!value) clear() }

    @Synchronized fun contains(key: String): Boolean = enabled && entries.containsKey(key)

    /** A read-only view of the file (own position), or null. Counts as a use. */
    @Synchronized fun get(key: String): ByteBuffer? = if (enabled) entries[key]?.asReadOnlyBuffer() else null

    fun fits(size: Long): Boolean = size in 1..maxFileBytes

    /** Stores a complete file; [keep] (the playing track) is never evicted to make room. False if too big or disabled. */
    @Synchronized fun put(key: String, data: ByteBuffer, keep: String? = null): Boolean {
        if (!enabled || !fits(data.capacity().toLong())) return false
        entries[key] = data
        var total = entries.values.sumOf { it.capacity().toLong() }
        while (entries.size > maxEntries || total > budgetBytes) {
            val victim = entries.keys.firstOrNull { it != key && it != keep } ?: break
            total -= entries.remove(victim)!!.capacity()
        }
        return true
    }

    @Synchronized fun clearExcept(keep: String?) { entries.keys.retainAll { it == keep } }

    @Synchronized fun clear() = entries.clear()

    @Synchronized fun bytes(): Long = entries.values.sumOf { it.capacity().toLong() }

    companion object {
        const val MAX_FILE_BYTES = 100L * 1024 * 1024
        const val BUDGET_BYTES = 192L * 1024 * 1024
        const val MAX_ENTRIES = 3

        /** Changes with the file, so a replaced file never plays from old data. */
        fun keyFor(t: Track): String = "${t.id}|${t.mtime}|${t.size}"
    }
}

/** Serves ranges of a cached file; no network. */
@UnstableApi
class MemoryDataSource(private val file: ByteBuffer) : BaseDataSource(/* isNetwork = */ false) {
    private var data: ByteBuffer? = null
    private var uri: Uri? = null
    private var position = 0
    private var remaining = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val d = file.duplicate()
        if (dataSpec.position > d.capacity()) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        data = d
        position = dataSpec.position.toInt()
        val left = d.capacity() - position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, left.toLong()).toInt() else left
        opened = true
        transferStarted(dataSpec)
        return remaining.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0) return C.RESULT_END_OF_INPUT
        val d = data ?: return C.RESULT_END_OF_INPUT
        val n = minOf(length, remaining)
        d.position(position)
        d.get(buffer, offset, n)
        position += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        data = null
        if (opened) { opened = false; transferEnded() }
    }
}
