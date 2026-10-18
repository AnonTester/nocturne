package app.nocturne.library

import app.nocturne.data.Track
import app.nocturne.data.TrackDao

/**
 * Brings the database in line with a fresh scan of one source.
 * New and changed files are written; unchanged files keep their already-read tags; files that vanished are removed
 * only when [deleteMissing] is true (i.e. the scan was complete).
 */
class Reconciler(private val dao: TrackDao) {
    /** Returns the number of tracks that were not in the database before. */
    suspend fun reconcile(source: String, scanned: List<Track>, deleteMissing: Boolean = true): Int {
        val existing = dao.stamps(source).associateBy { it.id }
        val changed = scanned.filter { t ->
            val e = existing[t.id]
            e == null || e.mtime != t.mtime || e.size != t.size
        }
        val present = scanned.mapTo(HashSet(scanned.size * 2)) { it.id }
        val removed = if (deleteMissing) existing.keys.filter { it !in present } else emptyList()
        changed.chunked(500).forEach { dao.upsert(it) }
        removed.chunked(500).forEach { dao.deleteByIds(it) }
        return changed.count { it.id !in existing }
    }
}
