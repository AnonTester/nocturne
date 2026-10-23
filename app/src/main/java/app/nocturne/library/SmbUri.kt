package app.nocturne.library

import android.net.Uri
import app.nocturne.data.Source

/** smb://host/share/dir/file.mp3[?src=<shareId>] <-> share-relative path used by smbj (backslash separated). */
object SmbUri {
    /** [sourceId] is added as `?src=` for added shares so playback finds the right connection; the main share has none. */
    fun build(host: String, share: String, relativePath: String, sourceId: String = Source.SAMBA): Uri {
        val b = Uri.Builder().scheme("smb").authority(host).appendPath(share)
        relativePath.split('/').filter { it.isNotEmpty() }.forEach { b.appendPath(it) }
        if (sourceId != Source.SAMBA) b.appendQueryParameter(SRC, sourceId)
        return b.build()
    }

    /** Path inside the share (segments after the share name), backslash separated. */
    fun sharePath(uri: Uri): String = uri.pathSegments.drop(1).joinToString("\\")

    /** Which share a track URI belongs to; URIs without `src` (everything saved before added shares existed) are the main share. */
    fun sourceOf(uri: Uri): String = uri.getQueryParameter(SRC) ?: Source.SAMBA

    /** Track id: the main share keeps its original "smb:<path>" form, added shares are namespaced so equal paths can't collide. */
    fun trackId(sourceId: String, relativePath: String): String =
        if (sourceId == Source.SAMBA) "smb:$relativePath" else "smb:$sourceId:$relativePath"

    private const val SRC = "src"
}
