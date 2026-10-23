package app.nocturne.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Source ids stored in [Track.source]: [LOCAL] = this device, [SAMBA] = the main Samba share, anything else = an added share
 * ("smb-xxxxxxxx"). Everything that is not [LOCAL] is a Samba share.
 */
object Source {
    const val LOCAL = "local"
    const val SAMBA = "samba"

    fun isSmb(id: String) = id != LOCAL

    fun newShareId() = "smb-" + java.util.UUID.randomUUID().toString().take(8)
}

@Entity(
    tableName = "tracks",
    indices = [Index("source"), Index("folderPath"), Index("metaDone")],
)
data class Track(
    /** Stable across rescans: "smb:<path>" (main share), "smb:<shareId>:<path>" (added share) or "local:<content uri>". */
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    /** Display name of the parent folder (album folder in the browser). */
    val folder: String,
    /** Full parent path, so two folders called "CD1" are not merged. */
    val folderPath: String,
    val filename: String,
    /** Playback URI: smb://host/share/path or content://... */
    val uri: String,
    val durationMs: Long = 0,
    val size: Long = 0,
    val mtime: Long = 0,
    val source: String,
    val hasCover: Boolean = false,
    /** True once tags/duration were read from the file (SMB tracks start filename-based). */
    val metaDone: Boolean = false,
)

data class FolderRow(
    val folderPath: String,
    val name: String,
    val trackCount: Int,
    val sampleArtist: String,
    val source: String,
)

data class SourceCount(val source: String, val count: Int)

/** Light row used to diff a rescan against the database. */
data class TrackStamp(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "mtime") val mtime: Long,
    @ColumnInfo(name = "size") val size: Long,
)
