package app.nocturne.library

import app.nocturne.data.SmbConfig
import app.nocturne.data.Source
import app.nocturne.data.Track
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.protocol.commons.EnumWithValue

/** Result of a scan. [unreadableFolders] > 0 means the track list may be incomplete and must not be used to delete anything. */
class SmbScanResult(val tracks: List<Track>, val unreadableFolders: Int)

/** Recursive audio scan of the configured share/subfolder. First pass is filename-based; tags come later. */
class SmbScanner(private val provider: SmbShareProvider) {
    fun scan(cfg: SmbConfig, isActive: () -> Boolean, onProgress: (Int) -> Unit): SmbScanResult {
        var unreadable = 0
        val root = cfg.subfolder.trim('/', '\\').replace('\\', '/')
        val out = ArrayList<Track>()
        val pending = ArrayDeque<String>()
        pending.add(root)

        while (pending.isNotEmpty() && isActive()) {
            val dir = pending.removeLast()
            val entries = when (val r = list(dir)) {
                is Listing.Ok -> r.entries
                Listing.Gone -> continue // folder disappeared while scanning: nothing to index
                is Listing.Failed -> {
                    // The root failing is a real error. A subfolder failing means the result is incomplete.
                    if (dir == root) throw r.error
                    unreadable++
                    continue
                }
            }
            for (entry in entries) {
                val name = entry.fileName
                if (name == "." || name == ".." || FileNameParser.isHidden(name)) continue
                val rel = if (dir.isEmpty()) name else "$dir/$name"
                val isDir = EnumWithValue.EnumUtils.isSet(entry.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
                if (isDir) {
                    pending.add(rel)
                } else if (FileNameParser.isAudio(name)) {
                    out.add(toTrack(cfg, dir, rel, name, entry.endOfFile, entry.lastWriteTime.toEpochMillis()))
                    if (out.size % 100 == 0) onProgress(out.size)
                }
            }
        }
        onProgress(out.size)
        return SmbScanResult(out, unreadable)
    }

    private sealed interface Listing {
        class Ok(val entries: List<com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation>) : Listing
        data object Gone : Listing
        class Failed(val error: Exception) : Listing
    }

    /** Lists one folder, reconnecting and retrying on transient errors (timeouts, dropped connections). */
    private fun list(dir: String): Listing {
        var last: Exception? = null
        for (attempt in 0 until LIST_ATTEMPTS) {
            try {
                return Listing.Ok(provider.share().list(dir.replace('/', '\\')))
            } catch (e: Exception) {
                val text = e.toString()
                if ("NO_SUCH_FILE" in text || "OBJECT_NAME_NOT_FOUND" in text || "OBJECT_PATH_NOT_FOUND" in text) return Listing.Gone
                last = e
                provider.close()
                try { Thread.sleep(400L * (attempt + 1)) } catch (_: InterruptedException) { break }
            }
        }
        return Listing.Failed(last ?: IllegalStateException("listing failed"))
    }

    private companion object {
        const val LIST_ATTEMPTS = 3
    }

    private fun toTrack(cfg: SmbConfig, dir: String, rel: String, name: String, size: Long, mtime: Long): Track {
        val parsed = FileNameParser.parse(name, FileNameParser.artistFolderOf(rel))
        val folder = FileNameParser.folderOf(rel)
        return Track(
            id = "smb:$rel",
            title = parsed.title,
            artist = parsed.artist,
            album = if (folder != FileNameParser.ROOT_FOLDER) folder else "Samba Library",
            folder = folder,
            folderPath = dir,
            filename = name,
            uri = SmbUri.build(cfg.host.trim(), cfg.share.trim('/', '\\'), rel).toString(),
            size = size,
            mtime = mtime,
            source = Source.SAMBA,
        )
    }
}
