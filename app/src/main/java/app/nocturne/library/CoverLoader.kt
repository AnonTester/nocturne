package app.nocturne.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import app.nocturne.data.Source
import app.nocturne.data.Track
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Now-playing artwork: embedded picture, cached on disk after the first read. Null means "no cover". */
class CoverLoader(private val context: Context, private val smb: SmbProviders) {
    private val memory = LruCache<String, Bitmap>(6)
    private val dir = File(context.cacheDir, "covers").apply { mkdirs() }

    suspend fun load(track: Track): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(track.id)?.let { return@withContext it }
        val key = sha1(track.id)
        val hit = File(dir, "$key.jpg")
        val miss = File(dir, "$key.none")
        if (miss.exists()) return@withContext null
        if (hit.exists()) {
            decode(hit.readBytes())?.also { memory.put(track.id, it) }?.let { return@withContext it }
        }
        val bytes = extractEmbedded(track)
        if (bytes == null) {
            runCatching { miss.createNewFile() }
            return@withContext null
        }
        val bmp = decode(bytes)
        if (bmp == null) {
            runCatching { miss.createNewFile() }
        } else {
            runCatching { hit.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
            memory.put(track.id, bmp)
        }
        bmp
    }

    private fun extractEmbedded(track: Track): ByteArray? {
        val r = MediaMetadataRetriever()
        var src: SmbMediaDataSource? = null
        return try {
            if (Source.isSmb(track.source)) {
                src = SmbMediaDataSource(smb.get(track.source), SmbUri.sharePath(Uri.parse(track.uri)))
                r.setDataSource(src)
            } else {
                r.setDataSource(context, Uri.parse(track.uri))
            }
            r.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
            runCatching { src?.close() }
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
