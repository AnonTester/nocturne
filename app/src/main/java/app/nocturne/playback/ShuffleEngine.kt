package app.nocturne.playback

import kotlin.random.Random

/**
 * Non-repeating shuffle with a play history, ported from the web baseline (shuffleEngine.ts).
 * Works on track ids so a 20k+ library stays cheap.
 */
class ShuffleEngine(private val random: Random = Random.Default) {
    private val history = ArrayList<String>()
    private var pointer = -1

    fun reset() {
        history.clear()
        pointer = -1
    }

    fun recordPlay(trackId: String) {
        if (trackId.isEmpty()) return
        // Playing something new from the middle of history discards the forward part.
        if (pointer >= 0 && pointer < history.size - 1) {
            while (history.size > pointer + 1) history.removeAt(history.size - 1)
        }
        if (history.lastOrNull() != trackId) {
            history.add(trackId)
            if (history.size > MAX_HISTORY) history.removeAt(0)
        }
        pointer = history.size - 1
    }

    fun hasPrevious(): Boolean = pointer > 0

    /** Steps back through history; null when there is nothing earlier. */
    fun previous(available: Set<String>): String? {
        while (pointer > 0) {
            pointer--
            val id = history[pointer]
            if (id in available) return id
        }
        return null
    }

    /** Next id: forward through history if we stepped back, otherwise a random pick that avoids recent plays. */
    fun next(ids: List<String>, currentId: String?): String? {
        if (ids.isEmpty()) return null
        if (ids.size == 1) return ids[0].also { recordPlay(it) }

        if (pointer >= 0 && pointer < history.size - 1) {
            val available = ids.toHashSet()
            while (pointer < history.size - 1) {
                pointer++
                val id = history[pointer]
                if (id in available) return id
            }
        }

        val window = minOf((ids.size * 0.6).toInt(), MAX_EXCLUSION)
        val recent = HashSet<String>(window + 1)
        for (i in maxOf(0, history.size - window) until history.size) recent.add(history[i])
        if (currentId != null) recent.add(currentId)

        var candidates = ids.filter { it !in recent }
        if (candidates.isEmpty()) candidates = ids.filter { it != currentId }
        if (candidates.isEmpty()) candidates = ids

        val chosen = candidates[random.nextInt(candidates.size)]
        recordPlay(chosen)
        return chosen
    }

    companion object {
        const val MAX_HISTORY = 150
        const val MAX_EXCLUSION = 60
    }
}
