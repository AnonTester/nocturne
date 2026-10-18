package app.nocturne.library

import android.content.Context
import android.net.Uri
import app.nocturne.data.SecretStore
import app.nocturne.data.SettingsRepository
import app.nocturne.data.Source
import app.nocturne.data.Track
import app.nocturne.data.TrackDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LibraryStatus(
    val scanning: Boolean = false,
    val message: String? = null,
    val lastSmbScanAt: Long = 0,
    /** Set by background rescans that found new files; the UI shows a dismissible banner. */
    val newTracksFound: Int = 0,
)

class LibraryManager(
    context: Context,
    private val dao: TrackDao,
    private val settings: SettingsRepository,
    private val smb: SmbShareProvider,
    /** Tag reading gets its own connection so it can't starve scans (or playback, which has a third one). */
    smbEnrich: SmbShareProvider,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val local = LocalScanner(appContext)
    private val smbScanner = SmbScanner(smb)
    private val enricher = MetadataEnricher(appContext, dao, smbEnrich)
    // One lock per source: a long Samba scan must not block a quick device rescan (or the other way round).
    private val smbLock = Mutex()
    private val localLock = Mutex()

    private val _status = MutableStateFlow(LibraryStatus())
    val status: StateFlow<LibraryStatus> = _status.asStateFlow()

    fun start() {
        scope.launch(Dispatchers.IO) {
            val last = settings.flow.first().smbLastScan
            _status.update { it.copy(lastSmbScanAt = last) }
        }
        scope.launch(Dispatchers.IO) { enrichmentLoop() }
        scope.launch(Dispatchers.IO) { autoRescanLoop() }
    }

    /** Runs [block] in the library's own scope so scans survive closing dialogs, rotation and activity restarts. */
    fun launch(block: suspend () -> Unit): Job = scope.launch(Dispatchers.IO) { block() }

    fun dismissNewTracks() = _status.update { it.copy(newTracksFound = 0) }

    private inline fun MutableStateFlow<LibraryStatus>.update(f: (LibraryStatus) -> LibraryStatus) {
        value = f(value)
    }

    /** Scans the Samba share (if configured) and reconciles the library. Returns a user-readable result. */
    suspend fun scanSmb(background: Boolean = false): String = smbLock.withLock {
        val cfg = settings.flow.first().smb
        if (!cfg.isConfigured) return@withLock "No Samba share configured."
        _status.update { it.copy(scanning = true, message = "Scanning \\\\${cfg.host}\\${cfg.share} ...") }
        try {
            val result = withContext(Dispatchers.IO) {
                val ctx = currentCoroutineContext()
                smbScanner.scan(cfg, { ctx.isActive }) { n ->
                    _status.update { it.copy(message = "Scanning share... $n tracks found") }
                }
            }
            // A scan that couldn't read some folders only adds/updates; it never deletes, or a network hiccup would wipe albums.
            val complete = result.unreadableFolders == 0
            val added = withContext(Dispatchers.IO) { reconcile(Source.SAMBA, result.tracks, deleteMissing = complete) }
            val now = System.currentTimeMillis()
            if (complete) settings.setSmbLastScan(now)
            val msg = "Samba scan complete: ${result.tracks.size} tracks" + (if (added > 0) " ($added new)" else "") +
                (if (!complete) ". ${result.unreadableFolders} folders could not be read; nothing was removed, will retry later." else "")
            _status.update {
                it.copy(
                    scanning = false, message = msg, lastSmbScanAt = if (complete) now else it.lastSmbScanAt,
                    newTracksFound = if (background && added > 0) it.newTracksFound + added else it.newTracksFound,
                )
            }
            msg
        } catch (e: CancellationException) {
            _status.update { it.copy(scanning = false, message = "Samba scan cancelled") }
            throw e
        } catch (e: Exception) {
            smb.close()
            val msg = "Samba scan failed: " + SmbShareProvider.describe(e, cfg)
            _status.update { it.copy(scanning = false, message = msg) }
            msg
        }
    }

    /** Re-reads MediaStore and all picked folders. */
    suspend fun scanLocal(): String = localLock.withLock {
        val s = settings.flow.first()
        _status.update { it.copy(scanning = true, message = "Scanning device music...") }
        try {
            val found = withContext(Dispatchers.IO) {
                val all = LinkedHashMap<String, Track>()
                if (s.mediaStoreEnabled) local.scanMediaStore().forEach { all[it.id] = it }
                for (tree in s.localTreeUris) {
                    local.scanTree(Uri.parse(tree)) { true }.forEach { all.putIfAbsent(it.id, it) }
                }
                all.values.toList()
            }
            val added = withContext(Dispatchers.IO) { reconcile(Source.LOCAL, found) }
            val msg = "Device scan complete: ${found.size} tracks" + if (added > 0) " ($added new)" else ""
            _status.update { it.copy(scanning = false, message = msg) }
            msg
        } catch (e: CancellationException) {
            _status.update { it.copy(scanning = false, message = "Device scan cancelled") }
            throw e
        } catch (e: SecurityException) {
            val msg = "No permission to read device music. Grant access in Settings."
            _status.update { it.copy(scanning = false, message = msg) }
            msg
        } catch (e: Exception) {
            val msg = "Device scan failed: ${e.message}"
            _status.update { it.copy(scanning = false, message = msg) }
            msg
        }
    }

    suspend fun clearSamba() = withContext(Dispatchers.IO) { dao.deleteSource(Source.SAMBA) }

    private val reconciler = Reconciler(dao)

    private suspend fun reconcile(source: String, scanned: List<Track>, deleteMissing: Boolean = true): Int =
        reconciler.reconcile(source, scanned, deleteMissing)

    private suspend fun enrichmentLoop() {
        while (currentCoroutineContext().isActive) {
            val done = try { enricher.runBatch() } catch (e: Exception) { 0 }
            if (done == 0) delay(5_000)
        }
    }

    private suspend fun autoRescanLoop() {
        while (currentCoroutineContext().isActive) {
            delay(60_000)
            val cfg = settings.flow.first().smb
            if (!cfg.isConfigured || cfg.autoRescanMinutes <= 0) continue
            val last = _status.value.lastSmbScanAt
            if (last == 0L || System.currentTimeMillis() - last < cfg.autoRescanMinutes * 60_000L) continue
            if (!_status.value.scanning) scanSmb(background = true)
        }
    }
}
