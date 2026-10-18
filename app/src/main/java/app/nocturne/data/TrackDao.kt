package app.nocturne.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tracks: List<Track>)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun byId(id: String): Track?

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun observeById(id: String): Flow<Track?>

    @Query("SELECT COUNT(*) FROM tracks")
    fun countFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks WHERE source = :source")
    suspend fun count(source: String): Int

    @Query("SELECT id FROM tracks ORDER BY folderPath COLLATE NOCASE, filename COLLATE NOCASE")
    suspend fun allIds(): List<String>

    @Query("SELECT id FROM tracks WHERE folderPath = :folderPath AND source = :source ORDER BY filename COLLATE NOCASE")
    suspend fun idsInFolder(folderPath: String, source: String): List<String>

    @Query("SELECT id, mtime, size FROM tracks WHERE source = :source")
    suspend fun stamps(source: String): List<TrackStamp>

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM tracks WHERE source = :source")
    suspend fun deleteSource(source: String)

    @Query("DELETE FROM tracks")
    suspend fun clear()

    @Query(
        """SELECT * FROM tracks
           WHERE (:q = '' OR title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%'
                  OR album LIKE '%' || :q || '%' OR folder LIKE '%' || :q || '%')
           ORDER BY folderPath COLLATE NOCASE, filename COLLATE NOCASE""",
    )
    fun search(q: String): PagingSource<Int, Track>

    @Query(
        """SELECT folderPath, MIN(folder) AS name, COUNT(*) AS trackCount, MIN(artist) AS sampleArtist, source
           FROM tracks
           WHERE (:q = '' OR folder LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%')
           GROUP BY folderPath, source
           ORDER BY name COLLATE NOCASE""",
    )
    fun folders(q: String): Flow<List<FolderRow>>

    @Query("SELECT * FROM tracks WHERE folderPath = :folderPath ORDER BY filename COLLATE NOCASE")
    suspend fun tracksInFolder(folderPath: String): List<Track>

    @Query("SELECT * FROM tracks WHERE metaDone = 0 LIMIT :limit")
    suspend fun pendingMetadata(limit: Int): List<Track>

    @Query("SELECT COUNT(*) FROM tracks WHERE metaDone = 0")
    fun pendingCount(): Flow<Int>

    @Query(
        """UPDATE tracks SET title = :title, artist = :artist, album = :album, durationMs = :durationMs,
           hasCover = :hasCover, metaDone = 1 WHERE id = :id""",
    )
    suspend fun updateMetadata(id: String, title: String, artist: String, album: String, durationMs: Long, hasCover: Boolean)

    @Query("UPDATE tracks SET metaDone = 1 WHERE id = :id")
    suspend fun markMetaDone(id: String)
}
