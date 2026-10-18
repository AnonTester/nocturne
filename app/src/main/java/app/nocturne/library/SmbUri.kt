package app.nocturne.library

import android.net.Uri

/** smb://host/share/dir/file.mp3 <-> share-relative path used by smbj (backslash separated). */
object SmbUri {
    fun build(host: String, share: String, relativePath: String): Uri {
        val b = Uri.Builder().scheme("smb").authority(host).appendPath(share)
        relativePath.split('/').filter { it.isNotEmpty() }.forEach { b.appendPath(it) }
        return b.build()
    }

    /** Path inside the share (segments after the share name), backslash separated. */
    fun sharePath(uri: Uri): String = uri.pathSegments.drop(1).joinToString("\\")
}
