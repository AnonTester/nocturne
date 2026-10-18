package app.nocturne

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.nocturne.data.AbsConfig
import app.nocturne.data.AppMode
import app.nocturne.data.AppSettings
import app.nocturne.data.FolderRow
import app.nocturne.data.SmbConfig
import app.nocturne.data.Track
import app.nocturne.library.SmbShareProvider
import app.nocturne.library.TestResult
import app.nocturne.playback.QueueScope
import app.nocturne.ui.theme.ThemeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NocturneApp
    private val dao = app.db.tracks()

    val settings: StateFlow<AppSettings> =
        app.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, app.latestSettings)
    val player = app.engine.state
    val position = app.engine.position
    val events = app.engine.events
    val libraryStatus = app.library.status
    val trackCount: StateFlow<Int> = dao.countFlow().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val pendingMetadata: StateFlow<Int> = dao.pendingCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val absState = app.abs.state

    // ------------------------------------------------------------ library browsing

    val trackQuery = MutableStateFlow("")
    val tracksPaged: Flow<PagingData<Track>> = trackQuery.debounce(200).flatMapLatest { q ->
        Pager(PagingConfig(pageSize = 60, enablePlaceholders = false)) { dao.search(q.trim()) }.flow
    }.cachedIn(viewModelScope)

    val folderQuery = MutableStateFlow("")
    val folders: Flow<List<FolderRow>> = folderQuery.debounce(150).flatMapLatest { dao.folders(it.trim()) }

    suspend fun tracksIn(folderPath: String): List<Track> = dao.tracksInFolder(folderPath)

    suspend fun cover(track: Track): Bitmap? = app.covers.load(track)

    // ------------------------------------------------------------ playback

    fun play() = app.engine.play()
    fun pause() = app.engine.pause()
    fun toggle() = app.engine.toggle()
    fun next() = app.engine.next()
    fun previous() = app.engine.previous()
    fun seekTo(ms: Long) = app.engine.seekTo(ms)
    fun jump(ms: Long) = app.engine.jump(ms)
    fun toggleShuffle() = app.engine.toggleShuffle()
    fun cycleRepeat() = app.engine.cycleRepeat()
    fun setVolume(v: Float) = app.engine.setVolume(v)
    fun adjustVolume(d: Float) = app.engine.adjustVolume(d)
    fun toggleWhisper() = app.engine.toggleWhisper()
    fun toggleMute() = app.engine.toggleMute()
    fun playTrack(t: Track, scope: QueueScope? = null) = app.engine.playTrack(t, scope)
    fun playFolder(f: FolderRow, source: String, shuffle: Boolean) =
        app.engine.playFolder(f.folderPath, source, f.name, shuffle)
    fun randomizeAll() = app.engine.randomizeAll()
    fun startSleepTimer(minutes: Int, finishCurrent: Boolean) = app.engine.startSleepTimer(minutes, finishCurrent)
    fun cancelSleepTimer() = app.engine.cancelSleepTimer()
    fun extendSleepTimer(minutes: Int) = app.engine.extendSleepTimer(minutes)

    // ------------------------------------------------------------ settings

    fun setTheme(t: ThemeId) = viewModelScope.launch { app.settings.setTheme(t) }
    fun setKeepAwake(v: Boolean) = viewModelScope.launch { app.settings.setKeepAwake(v) }
    fun setFullscreen(v: Boolean) = viewModelScope.launch { app.settings.setFullscreen(v) }

    fun setMode(mode: AppMode) {
        viewModelScope.launch { app.settings.setMode(mode) }
        if (mode == AppMode.AUDIOBOOKSHELF) {
            app.engine.pause() // either/or: music stops when switching to audiobooks
            viewModelScope.launch { app.abs.refresh() }
        }
    }

    // ------------------------------------------------------------ samba / device library

    fun hasSmbPassword() = app.secrets.smbPassword.isNotEmpty()

    suspend fun testSmb(cfg: SmbConfig, password: String?): TestResult = withContext(Dispatchers.IO) {
        SmbShareProvider.test(cfg, password ?: app.secrets.smbPassword)
    }

    /** Saves the config (and password, if the user typed one) and rescans the share, independent of any screen. */
    fun saveSmbAndScan(cfg: SmbConfig, password: String?) {
        app.library.launch {
            if (password != null) app.secrets.smbPassword = password
            app.settings.setSmb(cfg)
            app.smb.close()
            app.smbPlayback.close()
            app.smbEnrich.close()
            app.library.scanSmb()
        }
    }

    fun scanLocal() {
        app.library.launch { app.library.scanLocal(); lastLocalScan = System.currentTimeMillis() }
    }

    private var lastLocalScan = 0L

    /** Called on every return to the app: picks up files added while we were away, without hammering MediaStore. */
    fun scanLocalIfStale() {
        if (System.currentTimeMillis() - lastLocalScan > 60_000 && !libraryStatus.value.scanning) scanLocal()
    }

    fun dismissNewTracks() = app.library.dismissNewTracks()

    fun setMediaStoreEnabled(v: Boolean) = viewModelScope.launch { app.settings.setMediaStoreEnabled(v) }

    fun addTree(uri: String) {
        app.library.launch {
            app.settings.setLocalTrees(settings.value.localTreeUris + uri)
            app.library.scanLocal()
        }
    }

    fun removeTree(uri: String) {
        app.library.launch {
            app.settings.setLocalTrees(settings.value.localTreeUris - uri)
            app.library.scanLocal()
        }
    }

    // ------------------------------------------------------------ audiobookshelf

    fun refreshAbs() = viewModelScope.launch { app.abs.refresh() }

    fun hasAbsPassword() = app.secrets.absPassword.isNotEmpty()

    suspend fun testAbs(url: String, user: String, password: String?): Pair<Boolean, String> {
        if (password != null) app.secrets.absPassword = password
        return app.abs.test(url, user, password ?: "")
    }

    suspend fun saveAbs(cfg: AbsConfig, password: String?) {
        if (password != null) {
            app.secrets.absPassword = password
            app.secrets.absToken = ""
        }
        app.settings.setAbs(cfg)
        app.secrets.absToken = ""
        app.abs.refresh()
    }
}
