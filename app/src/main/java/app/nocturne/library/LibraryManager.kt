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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LibraryStatus(
    val scanning: Boolean = false,
    val message: String? = null,
    /** Set by background rescans that found new files; the UI shows a dismissible banner. */
    val newTracksFound: Int = 0,
)

class LibraryManager(
    context: Context,
    private val dao: TrackDao,
    private val settings: SettingsRepository,
    private val smb: SmbProviders,
    /** Tag reading gets its own connections so it can't starve scans (or playback, which has a third set). */
    private val smbEnrich: SmbProviders,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val local = LocalScanner(appContext)
    private val smbScanner = SmbScanner()
    private val enricher = MetadataEnricher(appContext, dao, smbEnrich)
    // One lock per source: a long scan of one share must not block a quick device rescan or another share.
    private val smbLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private fun smbLock(sourceId: String) = smbLocks.getOrPut(sourceId) { Mutex() }
    private val localLock = Mutex()
    private var activeScans = 0

    private val _status = MutableStateFlow(LibraryStatus())
    val status: StateFlow<LibraryStatus> = _status.asStateFlow()

    fun start() {
        scope.launch(Dispatchers.IO) { enrichmentLoop() }
        scope.launch(Dispatchers.IO) { autoRescanLoop() }
    }

    /** Runs [block] in the library's own scope so scans survive closing dialogs, rotation and activity restarts. */
    fun launch(block: suspend () -> Unit): Job = scope.launch(Dispatchers.IO) { block() }

    fun dismissNewTracks() = _status.update { it.copy(newTracksFound = 0) }

    private fun scanStarted(message: String) {
        synchronized(this) { activeScans++ }
        _status.update { it.copy(scanning = true, message = message) }
    }

    /** Several shares can scan at once; "scanning" stays true until the last one is done. */
    private fun scanEnded(message: String, more: (LibraryStatus) -> LibraryStatus = { it }) {
        val others = synchronized(this) { --activeScans > 0 }
        _status.update { more(it).copy(scanning = others, message = message) }
    }

    /** Scans one Samba share (if configured) and reconciles its tracks. Returns a user-readable result. */
    suspend fun scanSmb(sourceId: String = Source.SAMBA, background: Boolean = false): String = smbLock(sourceId).withLock {
        val src = settings.flow.first().smbSource(sourceId) ?: return@withLock "Share not found."
        val cfg = src.config
        val label = if (src.isMain) "Samba" else src.name
        if (!cfg.isConfigured) return@withLock "No Samba share configured for $label."
        scanStarted("Scanning $label: \\\\${cfg.host}\\${cfg.share} ...")
        try {
            val result = withContext(Dispatchers.IO) {
                val ctx = currentCoroutineContext()
                smbScanner.scan(smb.get(sourceId), sourceId, cfg, { ctx.isActive }) { n ->
                    _status.update { it.copy(message = "Scanning $label... $n tracks found") }
                }
            }
            // A scan that couldn't read some folders only adds/updates; it never deletes, or a network hiccup would wipe albums.
            val complete = result.unreadableFolders == 0
            val added = withContext(Dispatchers.IO) { reconcile(sourceId, result.tracks, deleteMissing = complete) }
            val now = System.currentTimeMillis()
            if (complete) settings.setSmbSourceLastScan(sourceId, now)
            val msg = "$label scan complete: ${result.tracks.size} tracks" + (if (added > 0) " ($added new)" else "") +
                (if (!complete) ". ${result.unreadableFolders} folders could not be read; nothing was removed, will retry later." else "")
            scanEnded(msg) {
                it.copy(newTracksFound = if (background && added > 0 && src.includeInMain) it.newTracksFound + added else it.newTracksFound)
            }
            msg
        } catch (e: CancellationException) {
            scanEnded("$label scan cancelled")
            throw e
        } catch (e: Exception) {
            smb.close(sourceId)
            val msg = "$label scan failed: " + SmbShareProvider.describe(e, cfg)
            scanEnded(msg)
            msg
        }
    }

    /** Re-reads MediaStore and all picked folders. */
    suspend fun scanLocal(): String = localLock.withLock {
        val s = settings.flow.first()
        scanStarted("Scanning device music...")
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
            scanEnded(msg)
            msg
        } catch (e: CancellationException) {
            scanEnded("Device scan cancelled")
            throw e
        } catch (e: SecurityException) {
            val msg = "No permission to read device music. Grant access in Settings."
            scanEnded(msg)
            msg
        } catch (e: Exception) {
            val msg = "Device scan failed: ${e.message}"
            scanEnded(msg)
            msg
        }
    }

    /** Deletes an added share: its tracks, its connections and its settings. The main share cannot be removed. */
    suspend fun removeSource(sourceId: String) {
        if (sourceId == Source.SAMBA || sourceId == Source.LOCAL) return
        smbLock(sourceId).withLock {
            withContext(Dispatchers.IO) { dao.deleteSource(sourceId) }
            settings.removeSmbSource(sourceId)
            smb.remove(sourceId)
            smbEnrich.remove(sourceId)
        }
    }

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
            for (src in settings.flow.first().smbSources) {
                val cfg = src.config
                if (!cfg.isConfigured || cfg.autoRescanMinutes <= 0) continue
                if (src.lastScan == 0L || System.currentTimeMillis() - src.lastScan < cfg.autoRescanMinutes * 60_000L) continue
                if (!smbLock(src.id).isLocked) scanSmb(src.id, background = true)
            }
        }
    }
}
