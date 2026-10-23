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

    /**
     * A pick for [next] without recording it in the history (it does draw from the random generator, so pass the result back as `preferred`). Lets the engine decide the next
     * track one step early, e.g. to measure its loudness before it starts.
     */
    fun peekNext(ids: List<String>, currentId: String?): String? {
        if (ids.isEmpty()) return null
        if (ids.size == 1) return ids[0]
        if (pointer >= 0 && pointer < history.size - 1) {
            val available = ids.toHashSet()
            for (i in pointer + 1 until history.size) if (history[i] in available) return history[i]
        }
        return choose(ids, currentId)
    }

    /**
     * Next id: forward through history if we stepped back, otherwise a random pick that avoids recent plays.
     * [preferred] (from [peekNext]) is honoured when it is still in [ids].
     */
    fun next(ids: List<String>, currentId: String?, preferred: String? = null): String? {
        if (ids.isEmpty()) return null
        if (ids.size == 1) return ids[0].also { recordPlay(it) }

        if (preferred != null && preferred != currentId && preferred in ids) {
            val forward = (pointer + 1 until history.size).firstOrNull { history[it] == preferred }
            if (forward != null) pointer = forward else recordPlay(preferred)
            return preferred
        }

        if (pointer >= 0 && pointer < history.size - 1) {
            val available = ids.toHashSet()
            while (pointer < history.size - 1) {
                pointer++
                val id = history[pointer]
                if (id in available) return id
            }
        }

        val chosen = choose(ids, currentId)
        recordPlay(chosen)
        return chosen
    }

    private fun choose(ids: List<String>, currentId: String?): String {
        val window = minOf((ids.size * 0.6).toInt(), MAX_EXCLUSION)
        val recent = HashSet<String>(window + 1)
        for (i in maxOf(0, history.size - window) until history.size) recent.add(history[i])
        if (currentId != null) recent.add(currentId)

        var candidates = ids.filter { it !in recent }
        if (candidates.isEmpty()) candidates = ids.filter { it != currentId }
        if (candidates.isEmpty()) candidates = ids
        return candidates[random.nextInt(candidates.size)]
    }

    companion object {
        const val MAX_HISTORY = 150
        const val MAX_EXCLUSION = 60
    }
}
