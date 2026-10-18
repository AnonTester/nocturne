package app.nocturne.library

/** Filename heuristics shared by the SMB and local scanners (same rules as the web baseline). */
object FileNameParser {
    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "flac", "ogg", "wav", "aac", "opus")

    data class Parsed(val artist: String, val title: String)

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun isAudio(name: String): Boolean = extensionOf(name) in AUDIO_EXTENSIONS

    fun isHidden(name: String): Boolean = name.startsWith(".") || name.startsWith("$")

    private val TRACK_NUMBER_PREFIX = Regex("^\\s*\\d{1,3}\\s*(?:[-._)]\\s*|\\s+)(?=\\S)")
    private val NUMBER_ONLY = Regex("^\\d{1,3}$")

    /**
     * "Artist - Title.mp3" -> artist/title. A leading track number ("09 - Title", "09. Title", "09 Title") is dropped,
     * and then [fallbackArtist] (usually the artist folder two levels up) is used when the name has no artist part.
     */
    fun parse(fileName: String, fallbackArtist: String? = null): Parsed {
        val base = (if ('.' in fileName) fileName.substringBeforeLast('.') else fileName).trim()
        val parts = base.split(" - ").map { it.trim() }
        // "09 - Title": the first part is only a track number, not an artist.
        if (parts.size >= 2 && NUMBER_ONLY.matches(parts[0])) {
            val rest = parts.drop(1)
            // "01 - Artist - Title" has an artist; "01 - Title" borrows it from the folder.
            return if (rest.size >= 2) Parsed(rest[0], rest.drop(1).joinToString(" - "))
            else Parsed(fallbackArtist ?: UNKNOWN_ARTIST, rest[0])
        }
        if (parts.size >= 2) return Parsed(parts[0], parts.drop(1).joinToString(" - "))
        val stripped = base.replace(TRACK_NUMBER_PREFIX, "").ifEmpty { base }
        return Parsed(fallbackArtist ?: UNKNOWN_ARTIST, stripped)
    }

    /** Folder two levels above the file ("Artist/Album/track.mp3" -> "Artist"), when the path is deep enough. */
    fun artistFolderOf(relativePath: String): String? {
        val parts = relativePath.trim('/').split('/')
        return if (parts.size >= 3) parts[parts.size - 3].takeIf { it.isNotBlank() } else null
    }

    /** Folder shown in the album browser: the immediate parent directory of the file. */
    fun folderOf(relativePath: String): String {
        val parts = relativePath.trim('/').split('/')
        return if (parts.size > 1) parts[parts.size - 2] else ROOT_FOLDER
    }

    const val UNKNOWN_ARTIST = "Unknown Artist"
    const val ROOT_FOLDER = "Root"
}
