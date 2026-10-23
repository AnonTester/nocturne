package app.nocturne.playback

import app.nocturne.data.RepeatMode
import app.nocturne.data.Track

enum class PlayStatus { IDLE, LOADING, PLAYING, PAUSED, ERROR }

data class PlayerState(
    val track: Track? = null,
    val status: PlayStatus = PlayStatus.IDLE,
    val durationMs: Long = 0,
    val volume: Float = 25f,
    val whisper: Boolean = false,
    val muted: Boolean = false,
    val shuffle: Boolean = true,
    val repeat: RepeatMode = RepeatMode.ALL,
    val sleep: SleepTimerState = SleepTimerState(),
    val queueSize: Int = 0,
    /** Name of the folder being played, or null when playing the whole library. */
    val scopeName: String? = null,
    val error: String? = null,
    /** Loudness levelling applied to the current track in dB; null when levelling is off. */
    val levelDb: Float? = null,
)

sealed interface EngineEvent {
    /** Sleep timer ended playback: the UI dims the screen (as the web app did). */
    data object SleepFinished : EngineEvent
}

sealed interface QueueScope {
    data object All : QueueScope
    data class Folder(val folderPath: String, val source: String, val name: String) : QueueScope
    /** Everything of one source (e.g. a share that is kept out of the main library). */
    data class Source(val id: String, val name: String) : QueueScope
}
