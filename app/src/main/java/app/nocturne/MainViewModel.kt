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
import app.nocturne.data.GainSource
import app.nocturne.playback.GainModel
import app.nocturne.data.SmbConfig
import app.nocturne.data.SmbSource
import app.nocturne.data.Source
import app.nocturne.data.Track
import app.nocturne.library.FolderListing
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
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
    private val mergedSources: Flow<List<String>> = settings.map { it.mergedSources }.distinctUntilChanged()
    /** Tracks in the main library (the merged sources): what "Randomize" and the status line talk about. */
    val trackCount: StateFlow<Int> = mergedSources.flatMapLatest { dao.countIn(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    /** Tracks per source id, for the source chips and the share list in Settings. */
    val sourceCounts: StateFlow<Map<String, Int>> =
        dao.countsBySource().map { rows -> rows.associate { it.source to it.count } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val pendingMetadata: StateFlow<Int> = dao.pendingCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val absState = app.abs.state

    // ------------------------------------------------------------ library browsing

    /** Source shown in the Library / Albums lists; null = the merged main library. */
    val trackSource = MutableStateFlow<String?>(null)
    val folderSource = MutableStateFlow<String?>(null)

    val trackQuery = MutableStateFlow("")
    val tracksPaged: Flow<PagingData<Track>> =
        combine(trackQuery.debounce(200), trackSource, mergedSources) { q, src, merged -> Triple(q, src, merged) }
            .flatMapLatest { (q, src, merged) ->
                Pager(PagingConfig(pageSize = 60, enablePlaceholders = false)) { dao.search(q.trim(), src?.let(::listOf) ?: merged) }.flow
            }.cachedIn(viewModelScope)

    val folderQuery = MutableStateFlow("")
    val folders: Flow<List<FolderRow>> =
        combine(folderQuery.debounce(150), folderSource, mergedSources) { q, src, merged -> Triple(q, src, merged) }
            .flatMapLatest { (q, src, merged) -> dao.folders(q.trim(), src?.let(::listOf) ?: merged) }
            // CD1..CD5 of one album sort together under the album's name, not under "C".
            .map { rows -> rows.sortedBy { it.displayName.lowercase() } }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)

    suspend fun tracksIn(folderPath: String, source: String): List<Track> = dao.tracksInFolder(folderPath, source)

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
        app.engine.playFolder(f.folderPath, source, f.displayName, shuffle)
    fun randomizeAll() = app.engine.randomizeAll()
    fun playSource(id: String, shuffle: Boolean) = app.engine.playSource(id, settings.value.sourceName(id), shuffle)
    fun startSleepTimer(minutes: Int, finishCurrent: Boolean) = app.engine.startSleepTimer(minutes, finishCurrent)
    fun cancelSleepTimer() = app.engine.cancelSleepTimer()
    fun extendSleepTimer(minutes: Int) = app.engine.extendSleepTimer(minutes)

    // ------------------------------------------------------------ settings

    fun setTheme(t: ThemeId) = viewModelScope.launch { app.settings.setTheme(t) }
    fun setKeepAwake(v: Boolean) = viewModelScope.launch { app.settings.setKeepAwake(v) }
    fun setFullscreen(v: Boolean) = viewModelScope.launch { app.settings.setFullscreen(v) }
    fun setAutoDimSeconds(v: Int) = viewModelScope.launch { app.settings.setAutoDimSeconds(v) }

    fun setMode(mode: AppMode) {
        viewModelScope.launch { app.settings.setMode(mode) }
        if (mode == AppMode.AUDIOBOOKSHELF) {
            app.engine.pause() // either/or: music stops when switching to audiobooks
            viewModelScope.launch { app.abs.refresh() }
        }
    }

    // ------------------------------------------------------------ samba / device library

    fun hasSmbPassword(sourceId: String = Source.SAMBA) = app.secrets.smbPasswordFor(sourceId).isNotEmpty()

    suspend fun testSmb(sourceId: String, cfg: SmbConfig, password: String?, isNew: Boolean = false): TestResult = withContext(Dispatchers.IO) {
        SmbShareProvider.test(cfg, password ?: sharedPassword(sourceId, cfg, isNew))
    }

    /** Lists the sub-folders of [path] on a share for the folder picker (same password rules as "Test connection"). */
    suspend fun listSmbFolders(sourceId: String, cfg: SmbConfig, password: String?, isNew: Boolean, path: String): FolderListing =
        withContext(Dispatchers.IO) {
            SmbShareProvider.listFolders(cfg, password ?: sharedPassword(sourceId, cfg, isNew), path)
        }

    /** A new share on the same server and user as the main one may reuse the main share's saved password. */
    private fun sharedPassword(sourceId: String, cfg: SmbConfig, isNew: Boolean): String {
        val own = app.secrets.smbPasswordFor(sourceId)
        if (own.isNotEmpty() || !isNew) return own
        val main = settings.value.smb
        val same = main.host.trim().equals(cfg.host.trim(), ignoreCase = true) && main.username.trim().equals(cfg.username.trim(), ignoreCase = true)
        return if (same) app.secrets.smbPasswordFor(Source.SAMBA) else ""
    }

    /** Whether leaving the password empty for this new share would reuse the main share's password. */
    fun reusesMainPassword(cfg: SmbConfig): Boolean {
        val main = settings.value.smb
        return app.secrets.smbPasswordFor(Source.SAMBA).isNotEmpty() &&
            main.host.trim().equals(cfg.host.trim(), ignoreCase = true) && main.username.trim().equals(cfg.username.trim(), ignoreCase = true)
    }

    /** Saves a share (and its password, if the user typed one) and rescans it, independent of any screen. */
    fun saveSmbAndScan(src: SmbSource, password: String?, isNew: Boolean = false) {
        app.library.launch {
            val pw = password ?: if (isNew) sharedPassword(src.id, src.config, isNew = true).takeIf { it.isNotEmpty() } else null
            if (pw != null) app.secrets.setSmbPasswordFor(src.id, pw)
            if (src.isMain) app.settings.setSmb(src.config) else app.settings.upsertSmbSource(src)
            app.smb.close(src.id)
            app.smbPlayback.close(src.id)
            app.smbEnrich.close(src.id)
            app.smbPrefetch.close(src.id)
            app.smbGain.close(src.id)
            app.library.scanSmb(src.id)
        }
    }

    /** Only the name and the "include in main library" flag changed: no rescan needed. */
    fun updateShareFlags(src: SmbSource) {
        viewModelScope.launch { app.settings.upsertSmbSource(src) }
    }

    fun removeShare(sourceId: String) {
        app.library.launch {
            app.library.removeSource(sourceId)
            app.secrets.removeSmbPassword(sourceId)
            app.smbPlayback.remove(sourceId)
            app.smbPrefetch.remove(sourceId)
        }
    }

    fun setDeviceInMain(v: Boolean) = viewModelScope.launch { app.settings.setDeviceInMain(v) }

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

    // ------------------------------------------------------------ loudness levelling

    /** Number of tracks per [app.nocturne.data.GainSource] value. */
    val gainCounts: StateFlow<Map<Int, Int>> =
        dao.gainCounts().map { rows -> rows.associate { it.gainSource to it.count } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setLevelTracks(v: Boolean) = viewModelScope.launch { app.settings.setLevelTracks(v) }
    fun setLevelOffsetDb(v: Float) = viewModelScope.launch { app.settings.setLevelOffsetDb(v) }
    /**
     * Sets the level offset so that a typical track (the library's median gain) keeps the loudness it had without levelling.
     * Returns false when too few tracks are known yet to say what typical is.
     */
    suspend fun matchOffsetToLibrary(): Boolean {
        val known = gainCounts.value.let { (it[GainSource.TAG] ?: 0) + (it[GainSource.MEASURED] ?: 0) }
        if (known < GainModel.MIN_KNOWN_FOR_MEDIAN) return false
        val median = withContext(Dispatchers.IO) { dao.medianGain() } ?: return false
        app.settings.setLevelOffsetDb(Math.round(-median).toFloat())
        return true
    }

    fun setGainScan(v: Boolean) = viewModelScope.launch { app.settings.setGainScan(v) }
    fun setRamCache(v: Boolean) = viewModelScope.launch { app.settings.setRamCache(v) }

    /** Forgets measured and failed loudness values so the background scan does them again. */
    fun remeasureAll() = viewModelScope.launch(Dispatchers.IO) { dao.resetMeasuredGain() }

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
