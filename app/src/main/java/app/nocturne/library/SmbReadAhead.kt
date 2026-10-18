package app.nocturne.library

import com.hierynomus.smbj.share.File as SmbFile

/**
 * Block buffer in front of an SMB file. Media parsers ask for a few hundred bytes at a time; without this every
 * such read is a network round trip (measured: ~260 B per read, thousands of reads per track).
 */
class SmbReadAhead(
    private val file: SmbFile,
    val size: Long,
    private val blockSize: Int = DEFAULT_BLOCK,
) {
    private val block = ByteArray(blockSize)
    private var start = -1L
    private var len = 0

    /** Reads up to [length] bytes at [position] into [dst]; returns -1 at end of file. */
    fun read(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        if (position < start || position >= start + len) {
            val want = minOf(blockSize.toLong(), size - position).toInt()
            val n = file.read(block, position, 0, want)
            if (n <= 0) return -1
            start = position
            len = n
        }
        val inBlock = (position - start).toInt()
        val count = minOf(length, len - inBlock)
        System.arraycopy(block, inBlock, dst, offset, count)
        return count
    }

    companion object {
        const val DEFAULT_BLOCK = 256 * 1024
    }
}
