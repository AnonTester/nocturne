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

    @Query("SELECT COUNT(*) FROM tracks WHERE source IN (:sources)")
    fun countIn(sources: List<String>): Flow<Int>

    @Query("SELECT source, COUNT(*) AS count FROM tracks GROUP BY source")
    fun countsBySource(): Flow<List<SourceCount>>

    @Query("SELECT COUNT(*) FROM tracks WHERE source = :source")
    suspend fun count(source: String): Int

    /** The queue of the main library: only the sources that are merged into it. */
    @Query("SELECT id FROM tracks WHERE source IN (:sources) ORDER BY folderPath COLLATE NOCASE, filename COLLATE NOCASE")
    suspend fun allIds(sources: List<String>): List<String>

    @Query("SELECT id FROM tracks WHERE source = :source ORDER BY folderPath COLLATE NOCASE, filename COLLATE NOCASE")
    suspend fun idsInSource(source: String): List<String>

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
           WHERE source IN (:sources) AND (:q = '' OR title LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%'
                  OR album LIKE '%' || :q || '%' OR folder LIKE '%' || :q || '%')
           ORDER BY folderPath COLLATE NOCASE, filename COLLATE NOCASE""",
    )
    fun search(q: String, sources: List<String>): PagingSource<Int, Track>

    @Query(
        """SELECT folderPath, MIN(folder) AS name, COUNT(*) AS trackCount, MIN(artist) AS sampleArtist, source
           FROM tracks
           WHERE source IN (:sources) AND (:q = '' OR folderPath LIKE '%' || :q || '%' OR artist LIKE '%' || :q || '%')
           GROUP BY folderPath, source
           ORDER BY name COLLATE NOCASE""",
    )
    fun folders(q: String, sources: List<String>): Flow<List<FolderRow>>

    @Query("SELECT * FROM tracks WHERE folderPath = :folderPath AND source = :source ORDER BY filename COLLATE NOCASE")
    suspend fun tracksInFolder(folderPath: String, source: String): List<Track>

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

    // ---------------------------------------------------------------- loudness

    /** Tracks in loudness state [state] (see GainSource): main share first, then other shares, device last; folder order within. */
    @Query(
        """SELECT * FROM tracks WHERE gainSource = :state
           ORDER BY CASE source WHEN 'samba' THEN 0 WHEN 'local' THEN 2 ELSE 1 END, source, folderPath COLLATE NOCASE, filename COLLATE NOCASE
           LIMIT :limit""",
    )
    suspend fun pendingGain(state: Int, limit: Int): List<Track>

    @Query("UPDATE tracks SET gainDb = :gainDb, peak = :peak, gainSource = :gainSource WHERE id = :id")
    suspend fun setGain(id: String, gainDb: Float?, peak: Float?, gainSource: Int)

    @Query("SELECT gainSource, COUNT(*) AS count FROM tracks GROUP BY gainSource")
    fun gainCounts(): Flow<List<GainCount>>

    /** Median of all known gains (tags and measured): what a track without a value is assumed to have. */
    @Query(
        """SELECT gainDb FROM tracks WHERE gainSource IN (1, 2) AND gainDb IS NOT NULL ORDER BY gainDb
           LIMIT 1 OFFSET (SELECT COUNT(*) FROM tracks WHERE gainSource IN (1, 2) AND gainDb IS NOT NULL) / 2""",
    )
    suspend fun medianGain(): Float?

    /** "Re-measure all": forget measured and failed values (tag values stay, tags are re-read when the file changes). */
    @Query("UPDATE tracks SET gainDb = NULL, peak = NULL, gainSource = 0 WHERE gainSource IN (2, 3, 4)")
    suspend fun resetMeasuredGain()
}
