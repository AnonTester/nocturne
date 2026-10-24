package app.nocturne.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.File as SmbFile
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Random-access SMB reader for ExoPlayer: seeks translate to ranged reads, so playback starts
 * instantly and nothing is downloaded whole.
 */
private const val TAG = "NocturneSMB"
private const val OPEN_DEADLINE_S = 5L
private val opener = Executors.newCachedThreadPool { r -> Thread(r, "smb-open").apply { isDaemon = true } }

@UnstableApi
class SmbDataSource(private val provider: SmbShareProvider) : BaseDataSource(/* isNetwork = */ true) {
    private var file: SmbFile? = null
    private var reader: SmbReadAhead? = null
    private var uri: Uri? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var opened = false
    private var openedAt = 0L
    private var readCalls = 0
    private var readBytes = 0L
    private var readNanos = 0L

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        openedAt = System.nanoTime(); readCalls = 0; readBytes = 0; readNanos = 0
        try {
            val f = openWithRetry(SmbUri.sharePath(dataSpec.uri))
            file = f
            val size = f.fileInformation.standardInformation.endOfFile
            reader = SmbReadAhead(f, size)
            if (dataSpec.position > size) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }
            position = dataSpec.position
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else size - position
            val took = (System.nanoTime() - openedAt) / 1_000_000
            Log.println(if (took > 1000) Log.WARN else Log.DEBUG, TAG, "open pos=${dataSpec.position} size=$size took=${took}ms")
        } catch (e: DataSourceException) {
            throw e
        } catch (e: Exception) {
            close()
            throw IOException("SMB open failed: ${e.message}", e)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    private fun openWithRetry(path: String): SmbFile {
        // A connection the NAS or Wi-Fi dropped silently makes the open wait for smbj's long timeout; give up sooner.
        fun attempt(): SmbFile {
            val pending = opener.submit(Callable {
                provider.share().openFile(
                    path,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                    SMB2CreateDisposition.FILE_OPEN,
                    null,
                )
            })
            try {
                return pending.get(OPEN_DEADLINE_S, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                pending.cancel(true)
                throw IOException("SMB open timed out after ${OPEN_DEADLINE_S}s")
            } catch (e: ExecutionException) {
                throw (e.cause as? Exception) ?: e
            }
        }
        return try {
            attempt()
        } catch (e: Exception) {
            // The cached connection may have gone stale (sleep, Wi-Fi roam): reconnect once.
            Log.w(TAG, "open failed (${e.message}), reconnecting")
            provider.close()
            attempt()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val r = reader ?: throw IOException("SMB file is not open")
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val r0 = System.nanoTime()
        val n = try {
            r.read(position, buffer, offset, toRead)
        } catch (e: Exception) {
            throw IOException("SMB read failed: ${e.message}", e)
        }
        readNanos += System.nanoTime() - r0
        if (n <= 0) return C.RESULT_END_OF_INPUT
        readCalls++; readBytes += n
        position += n
        bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (readCalls > 0) {
            Log.d(TAG, "close: $readCalls reads, ${readBytes / 1024} KiB, avg ${readBytes / readCalls} B/read, " +
                "read time ${readNanos / 1_000_000}ms total, open-to-close ${(System.nanoTime() - openedAt) / 1_000_000}ms")
        }
        uri = null
        runCatching { file?.close() }
        file = null
        reader = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}

/** Sends smb:// to an [SmbDataSource] on the track's own share (`?src=`) and everything else (content://, file://) to the default sources. */
@UnstableApi
class RoutingDataSource(context: Context, private val providers: SmbProviders, private val ram: TrackRamCache? = null) : DataSource {
    private val other = DefaultDataSource.Factory(context).createDataSource()
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
        other.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val cached = dataSpec.key?.let { ram?.get(it) }
        val target: DataSource = when {
            dataSpec.uri.scheme != "smb" -> other
            cached != null -> {
                Log.d(TAG, "memory pos=${dataSpec.position}")
                MemoryDataSource(cached).also { s -> listeners.forEach(s::addTransferListener) }
            }
            else -> SmbDataSource(providers.get(SmbUri.sourceOf(dataSpec.uri))).also { s -> listeners.forEach(s::addTransferListener) }
        }
        active = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        (active ?: throw IOException("DataSource not open")).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try { active?.close() } finally { active = null }
    }

    class Factory(private val context: Context, private val providers: SmbProviders, private val ram: TrackRamCache? = null) : DataSource.Factory {
        override fun createDataSource(): DataSource = RoutingDataSource(context, providers, ram)
    }
}
