package app.nocturne.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import app.nocturne.data.RepeatMode
import app.nocturne.data.SettingsRepository
import app.nocturne.data.Track
import app.nocturne.data.TrackDao
import app.nocturne.library.RoutingDataSource
import app.nocturne.library.SmbShareProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The single owner of audio playback (app-scoped, so timers and playback outlive any activity).
 * Queue/shuffle semantics follow the web baseline; ExoPlayer holds one item at a time.
 */
@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackEngine(
    private val context: Context,
    private val dao: TrackDao,
    private val settings: SettingsRepository,
    smb: SmbShareProvider,
    private val scope: CoroutineScope,
) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                RoutingDataSource.Factory(context, smb),
                DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true),
            ),
        )
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private val shuffle = ShuffleEngine()
    private var queueIds: List<String> = emptyList()
    private var queueScope: QueueScope = QueueScope.All
    private var loadedTrackId: String? = null
    private var fadeJob: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0L)
    /** Updated ~4x/s while playing; kept apart from [state] so only the seek bar recomposes. */
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onTrackEnded()
                syncStatus()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) { retryCount = 0; _state.update { if (it.error != null) it.copy(error = null) else it } }
                syncStatus()
            }
            override fun onPlayerError(error: PlaybackException) = handlePlayerError(error)
        })
        scope.launch { restore() }
        scope.launch { positionTicker() }
        scope.launch { sleepTicker() }
        scope.launch {
            // Tags are read in the background after a scan: keep the now-playing info in step with the database.
            _state.map { it.track?.id }.distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) emptyFlow() else dao.observeById(id) }
                .collect { fresh ->
                    if (fresh != null) _state.update { s -> if (s.track?.id == fresh.id) s.copy(track = fresh) else s }
                }
        }
        scope.launch {
            // Keep the queue in step with library changes (scans, enrichment-free: only count changes).
            dao.countFlow().debounce(800).collect { refreshQueue() }
        }
    }

    // ---------------------------------------------------------------- startup

    private suspend fun restore() {
        val s = settings.flow.first()
        _state.update { it.copy(volume = s.volume, whisper = s.whisper, shuffle = s.shuffle, repeat = s.repeat) }
        applyGain()
        refreshQueue()
        val track = (if (s.lastTrackId.isNotEmpty()) dao.byId(s.lastTrackId) else null)
            ?: queueIds.firstOrNull()?.let { dao.byId(it) }
        if (track != null) {
            _state.update { it.copy(track = track, durationMs = track.durationMs) }
            shuffle.recordPlay(track.id)
        }
    }

    private suspend fun refreshQueue() {
        queueIds = when (val q = queueScope) {
            QueueScope.All -> dao.allIds()
            is QueueScope.Folder -> dao.idsInFolder(q.folderPath, q.source)
        }
        _state.update { it.copy(queueSize = queueIds.size) }
        if (_state.value.track == null && queueIds.isNotEmpty()) {
            dao.byId(queueIds.first())?.let { t ->
                _state.update { it.copy(track = t, durationMs = t.durationMs) }
                shuffle.recordPlay(t.id)
            }
        }
    }

    // ---------------------------------------------------------------- transport

    fun play() {
        cancelFade()
        raiseDeviceVolume()
        val st = _state.value
        val track = st.track ?: return
        if (loadedTrackId != track.id) {
            load(track, autoPlay = true)
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
        ensureService()
    }

    fun pause() {
        cancelFade()
        player.pause()
    }

    fun toggle() = if (player.isPlaying || _state.value.status == PlayStatus.LOADING) pause() else play()

    /** Plays [track]; [scopeOverride] switches the queue (e.g. "play this album"). */
    fun playTrack(track: Track, scopeOverride: QueueScope? = null) {
        scope.launch {
            if (scopeOverride != null && scopeOverride != queueScope) {
                queueScope = scopeOverride
                refreshQueue()
            }
            _state.update { it.copy(scopeName = (queueScope as? QueueScope.Folder)?.name) }
            shuffle.recordPlay(track.id)
            cancelFade()
            load(track, autoPlay = true)
            ensureService()
        }
    }

    fun playFolder(folderPath: String, source: String, name: String, shuffleOn: Boolean) {
        scope.launch {
            val q = QueueScope.Folder(folderPath, source, name)
            queueScope = q
            refreshQueue()
            setShuffle(shuffleOn)
            shuffle.reset()
            val first = if (shuffleOn) queueIds.randomOrNull() else queueIds.firstOrNull()
            val track = first?.let { dao.byId(it) } ?: return@launch
            playTrack(track, q)
        }
    }

    /** "Randomize Playlist": back to the whole library, fresh shuffle history, next random track. */
    fun randomizeAll() {
        scope.launch {
            queueScope = QueueScope.All
            _state.update { it.copy(scopeName = null) }
            refreshQueue()
            shuffle.reset()
            setShuffle(true)
            next(userInitiated = true)
        }
    }

    fun next(userInitiated: Boolean = true) {
        scope.launch {
            val id = pickNext(wrap = userInitiated) ?: run { pause(); player.seekTo(0); return@launch }
            dao.byId(id)?.let { t ->
                if (!_state.value.shuffle) shuffle.recordPlay(t.id)
                cancelFade(); load(t, autoPlay = true); ensureService()
            }
        }
    }

    fun previous() {
        scope.launch {
            if (player.currentPosition > RESTART_THRESHOLD_MS) { player.seekTo(0); return@launch }
            val available = queueIds.toHashSet()
            val id = shuffle.previous(available) ?: run {
                val i = queueIds.indexOf(_state.value.track?.id)
                if (queueIds.isEmpty()) null else queueIds[if (i > 0) i - 1 else queueIds.lastIndex]
            } ?: return@launch
            dao.byId(id)?.let { cancelFade(); load(it, autoPlay = true); ensureService() }
        }
    }

    fun seekTo(ms: Long) {
        if (loadedTrackId == null) return
        val d = player.duration
        player.seekTo(if (d != C.TIME_UNSET) ms.coerceIn(0, d) else maxOf(0, ms))
        _position.value = player.currentPosition
    }

    fun jump(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    private fun pickNext(wrap: Boolean): String? {
        val ids = queueIds
        if (ids.isEmpty()) return null
        val st = _state.value
        if (st.shuffle) return shuffle.next(ids, st.track?.id)
        val i = ids.indexOf(st.track?.id)
        return when {
            i in 0 until ids.lastIndex -> ids[i + 1]
            wrap || st.repeat == RepeatMode.ALL -> ids[0]
            else -> null
        }
    }

    private fun onTrackEnded() {
        val (newTimer, action) = SleepTimer.onTrackEnded(_state.value.sleep)
        if (action == SleepAction.FADE_OUT_NOW) {
            _state.update { it.copy(sleep = newTimer) }
            _events.tryEmit(EngineEvent.SleepFinished)
            return
        }
        if (_state.value.repeat == RepeatMode.ONE) {
            player.seekTo(0); player.play()
        } else {
            next(userInitiated = false)
        }
    }

    // ---------------------------------------------------------------- errors

    private var retryCount = 0
    private var retryJob: Job? = null

    /** Wi-Fi roams, NAS sleeps: network/IO failures are retried with back-off from where playback stopped. */
    private fun handlePlayerError(error: PlaybackException) {
        val transient = generateSequence<Throwable>(error) { it.cause }.any { it is java.io.IOException }
        if (transient && retryCount < MAX_RETRIES) {
            retryCount++
            val resumeAt = player.currentPosition
            _state.update { it.copy(status = PlayStatus.LOADING, error = "Connection problem, retrying ($retryCount/$MAX_RETRIES)...") }
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(RETRY_BASE_MS * retryCount)
                player.prepare()
                player.seekTo(resumeAt)
                player.play()
            }
        } else {
            _state.update { it.copy(status = PlayStatus.ERROR, error = error.cause?.message ?: error.message) }
        }
    }

    // ---------------------------------------------------------------- loading

    private fun load(track: Track, autoPlay: Boolean) {
        retryCount = 0
        retryJob?.cancel()
        loadedTrackId = track.id
        _position.value = 0
        _state.update {
            it.copy(track = track, durationMs = track.durationMs, status = PlayStatus.LOADING, error = null)
        }
        val item = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.uri)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setAlbumTitle(track.album).build(),
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = autoPlay
        scope.launch { settings.setLastTrack(track.id) }
    }

    private fun syncStatus() {
        val status = when {
            player.isPlaying -> PlayStatus.PLAYING
            player.playbackState == Player.STATE_BUFFERING && player.playWhenReady -> PlayStatus.LOADING
            _state.value.status == PlayStatus.ERROR && player.playbackState == Player.STATE_IDLE -> PlayStatus.ERROR
            loadedTrackId == null -> PlayStatus.IDLE
            else -> PlayStatus.PAUSED
        }
        val d = player.duration
        _state.update {
            it.copy(status = status, durationMs = if (d != C.TIME_UNSET && d > 0) d else it.durationMs)
        }
    }

    private fun ensureService() {
        try {
            context.startService(Intent(context, PlaybackService::class.java))
        } catch (_: Exception) {
            // Not allowed to start from the background right now; playback still works, only the notification is delayed.
        }
    }

    // ---------------------------------------------------------------- volume

    /** The in-app slider is only a gain on top of Android's media volume, so a muted or low device volume made it look dead. */
    private fun raiseDeviceVolume() {
        scope.launch {
            if (!settings.flow.first().deviceVolumeMax) return@launch
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            if (am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) < max) {
                runCatching { am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, max, 0) }
            }
        }
    }

    private fun applyGain() {
        val s = _state.value
        player.volume = VolumeCurve.gain(s.volume, s.whisper, s.muted)
    }

    fun setVolume(v: Float) {
        _state.update { it.copy(volume = v.coerceIn(0f, 100f)) }
        applyGain()
        scope.launch { settings.setVolume(_state.value.volume) }
    }

    fun adjustVolume(delta: Float) = setVolume(VolumeCurve.step(_state.value.volume, delta))

    fun toggleWhisper() {
        _state.update { it.copy(whisper = !it.whisper) }
        applyGain()
        scope.launch { settings.setWhisper(_state.value.whisper) }
    }

    fun toggleMute() {
        _state.update { it.copy(muted = !it.muted) }
        applyGain()
    }

    fun setShuffle(on: Boolean) {
        _state.update { it.copy(shuffle = on) }
        scope.launch { settings.setShuffle(on) }
    }

    fun toggleShuffle() = setShuffle(!_state.value.shuffle)

    fun cycleRepeat() {
        val r = _state.value.repeat.next()
        _state.update { it.copy(repeat = r) }
        scope.launch { settings.setRepeat(r) }
    }

    // ---------------------------------------------------------------- sleep timer

    fun startSleepTimer(minutes: Int, finishCurrent: Boolean) {
        _state.update { it.copy(sleep = SleepTimer.start(System.currentTimeMillis(), minutes, finishCurrent)) }
    }

    fun cancelSleepTimer() = _state.update { it.copy(sleep = SleepTimer.cancel()) }

    fun extendSleepTimer(minutes: Int) = _state.update { it.copy(sleep = SleepTimer.extend(it.sleep, minutes)) }

    private suspend fun sleepTicker() {
        while (scope.isActive) {
            delay(1000)
            val cur = _state.value.sleep
            if (!cur.isActive) continue
            val (next, action) = SleepTimer.tick(cur, System.currentTimeMillis())
            if (next != cur) _state.update { it.copy(sleep = next) }
            if (action == SleepAction.FADE_OUT_NOW) {
                fadeOutAndPause(FADE_MS)
                _events.tryEmit(EngineEvent.SleepFinished)
            }
        }
    }

    /** Smooth bedside fade (30 steps), then pause and restore the user's volume. */
    fun fadeOutAndPause(durationMs: Long = FADE_MS) {
        cancelFade()
        fadeJob = scope.launch {
            val start = player.volume
            val steps = 30
            for (i in steps downTo 0) {
                delay(durationMs / steps)
                player.volume = start * i / steps
            }
            player.pause()
            applyGain()
        }
    }

    private fun cancelFade() {
        if (fadeJob?.isActive == true) {
            fadeJob?.cancel()
            applyGain()
        }
        fadeJob = null
    }

    private suspend fun positionTicker() {
        while (scope.isActive) {
            delay(250)
            if (player.isPlaying) _position.value = player.currentPosition
        }
    }

    companion object {
        const val RESTART_THRESHOLD_MS = 3000L
        const val FADE_MS = 3500L
        const val MAX_RETRIES = 4
        const val RETRY_BASE_MS = 1500L
    }
}
