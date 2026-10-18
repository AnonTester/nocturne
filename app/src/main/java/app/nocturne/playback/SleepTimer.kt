package app.nocturne.playback

data class SleepTimerState(
    val isActive: Boolean = false,
    val targetMillis: Long? = null,
    val durationMinutes: Int = 30,
    val remainingSeconds: Long = 0,
    val waitingForTrackToEnd: Boolean = false,
    val finishCurrentTrack: Boolean = true,
)

enum class SleepAction { NONE, FADE_OUT_NOW }

/** Pure sleep-timer state machine; the engine drives it with a 1 s tick. */
object SleepTimer {
    fun start(now: Long, minutes: Int, finishCurrent: Boolean) = SleepTimerState(
        isActive = true,
        targetMillis = now + minutes * 60_000L,
        durationMinutes = minutes,
        remainingSeconds = minutes * 60L,
        waitingForTrackToEnd = false,
        finishCurrentTrack = finishCurrent,
    )

    fun cancel() = SleepTimerState()

    fun extend(state: SleepTimerState, extraMinutes: Int): SleepTimerState {
        val target = state.targetMillis ?: return state
        if (!state.isActive) return state
        return state.copy(targetMillis = target + extraMinutes * 60_000L, waitingForTrackToEnd = false)
    }

    /** Returns the new state and whether playback must fade out right now. */
    fun tick(state: SleepTimerState, now: Long): Pair<SleepTimerState, SleepAction> {
        val target = state.targetMillis
        if (!state.isActive || target == null || state.waitingForTrackToEnd) return state to SleepAction.NONE
        val remainingMs = target - now
        if (remainingMs > 0) {
            return state.copy(remainingSeconds = (remainingMs + 999) / 1000) to SleepAction.NONE
        }
        return if (state.finishCurrentTrack) {
            state.copy(remainingSeconds = 0, waitingForTrackToEnd = true) to SleepAction.NONE
        } else {
            cancel() to SleepAction.FADE_OUT_NOW
        }
    }

    /** A track ended while the timer was waiting for it: fade out (brief) and clear the timer. */
    fun onTrackEnded(state: SleepTimerState): Pair<SleepTimerState, SleepAction> =
        if (state.isActive && state.waitingForTrackToEnd) cancel() to SleepAction.FADE_OUT_NOW
        else state to SleepAction.NONE
}
