package app.nocturne.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import app.nocturne.data.Source
import app.nocturne.data.Track

/** Local music: the MediaStore index plus user-picked folders (SD card, any directory) via SAF. */
class LocalScanner(private val context: Context) {

    fun scanMediaStore(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Audio.Media.RELATIVE_PATH)
            else add(MediaStore.Audio.Media.DATA)
        }.toTypedArray()
        // Skip ringtones/notifications/alarms; keep music, podcasts and everything else audible.
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0"
        val out = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val iMod = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val iPath = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            else c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val name = c.getString(iName) ?: continue
                if (!FileNameParser.isAudio(name) || FileNameParser.isHidden(name)) continue
                val raw = c.getString(iPath).orEmpty()
                val folderPath = if (Build.VERSION.SDK_INT >= 29) raw.trim('/') else raw.substringBeforeLast('/', "").trim('/')
                val folder = folderPath.substringAfterLast('/').ifEmpty { FileNameParser.ROOT_FOLDER }
                val uri = ContentUris.withAppendedId(collection, c.getLong(iId))
                val artist = c.getString(iArtist)?.takeUnless { it == "<unknown>" || it.isBlank() }
                val album = c.getString(iAlbum)?.takeUnless { it.isBlank() || it == folder && folder == "Music" }
                out.add(
                    Track(
                        id = "local:$uri",
                        title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: FileNameParser.parse(name).title,
                        artist = artist ?: FileNameParser.parse(name).artist,
                        album = album ?: folder,
                        folder = folder,
                        folderPath = folderPath,
                        filename = name,
                        uri = uri.toString(),
                        durationMs = c.getLong(iDur),
                        size = c.getLong(iSize),
                        mtime = c.getLong(iMod) * 1000,
                        source = Source.LOCAL,
                        metaDone = true,
                    ),
                )
            }
        }
        return out
    }

    /** Walks a document tree the user granted access to. Uses raw cursors, much faster than DocumentFile. */
    fun scanTree(treeUri: Uri, isActive: () -> Boolean): List<Track> {
        val out = ArrayList<Track>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = rootId.substringAfter(':').substringAfterLast('/').ifEmpty { rootId.substringBefore(':') }
        val pending = ArrayDeque<Pair<String, String>>() // documentId to display path
        pending.add(rootId to rootName)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        while (pending.isNotEmpty() && isActive()) {
            val (docId, path) = pending.removeLast()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1) ?: continue
                    if (FileNameParser.isHidden(name)) continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(id to "$path/$name")
                    } else if (FileNameParser.isAudio(name)) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        val folder = path.substringAfterLast('/')
                        val parsed = FileNameParser.parse(name, FileNameParser.artistFolderOf(path.substringAfter('/', "") + "/$name"))
                        out.add(
                            Track(
                                id = "local:$uri",
                                title = parsed.title,
                                artist = parsed.artist,
                                album = folder,
                                folder = folder,
                                folderPath = path,
                                filename = name,
                                uri = uri.toString(),
                                size = c.getLong(4),
                                mtime = c.getLong(3),
                                source = Source.LOCAL,
                            ),
                        )
                    }
                }
            }
        }
        return out
    }
}
