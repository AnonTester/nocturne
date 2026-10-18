package app.nocturne.library

import android.media.MediaDataSource
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import java.util.EnumSet

/** Lets MediaMetadataRetriever read tags/duration/cover straight off the share without downloading files. */
class SmbMediaDataSource(provider: SmbShareProvider, sharePath: String) : MediaDataSource() {
    private val file = provider.share().openFile(
        sharePath,
        EnumSet.of(AccessMask.GENERIC_READ),
        null,
        EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
        SMB2CreateDisposition.FILE_OPEN,
        null,
    )
    private val length = file.fileInformation.standardInformation.endOfFile

    private val reader = SmbReadAhead(file, length, blockSize = 1024 * 1024)

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        return reader.read(position, buffer, offset, size)
    }

    override fun getSize(): Long = length

    override fun close() {
        runCatching { file.close() }
    }
}
