package app.nocturne.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.nocturne.playback.GainModel
import app.nocturne.ui.theme.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("nocturne_settings")

enum class RepeatMode { OFF, ALL, ONE;
    fun next() = entries[(ordinal + 1) % entries.size]
}

enum class AppMode { MUSIC, AUDIOBOOKSHELF }

data class SmbConfig(
    val host: String = "",
    val share: String = "",
    val subfolder: String = "",
    val username: String = "",
    val domain: String = "WORKGROUP",
    val autoRescanMinutes: Int = 30,
) {
    val isConfigured get() = host.isNotBlank() && share.isNotBlank()
}

/**
 * One Samba share. The main share (id [Source.SAMBA]) is always part of the main library; added shares can be kept
 * out of it ([includeInMain] = false) so "Randomize" and the default Library view never contain them.
 */
data class SmbSource(
    val id: String,
    val name: String,
    val config: SmbConfig,
    val includeInMain: Boolean = false,
    val lastScan: Long = 0,
) {
    val isMain get() = id == Source.SAMBA
}

data class AbsConfig(
    val serverUrl: String = "",
    val username: String = "",
    val appPackage: String = "com.audiobookshelf.app",
)

data class AppSettings(
    val theme: ThemeId = ThemeId.OLED,
    val volume: Float = 25f,
    val whisper: Boolean = false,
    val shuffle: Boolean = true,
    val repeat: RepeatMode = RepeatMode.ALL,
    val keepAwake: Boolean = true,
    val fullscreen: Boolean = true,
    /** Seconds of inactivity before the dim screen starts by itself; 0 = never. */
    val autoDimSeconds: Int = 0,
    /** Level every track to the same loudness (ReplayGain tags, or measured when untagged). */
    val levelTracks: Boolean = false,
    /** Added to every track's gain, in dB (the overall level of the levelled music). */
    val levelOffsetDb: Float = 0f,
    /** Measure loudness of untagged tracks in the background. */
    val gainScan: Boolean = true,
    /** Keep the previous, current and next Samba track in memory (seeks and track changes then need no network). */
    val ramCache: Boolean = true,
    val mode: AppMode = AppMode.MUSIC,
    val lastTrackId: String = "",
    val smbLastScan: Long = 0,
    val mediaStoreEnabled: Boolean = true,
    val localTreeUris: Set<String> = emptySet(),
    /** The main share. Added shares are in [smbExtra]; use [smbSources] for all of them. */
    val smb: SmbConfig = SmbConfig(),
    val smbExtra: List<SmbSource> = emptyList(),
    /** Device music is part of the main library (random / shuffle all) when true. */
    val deviceInMain: Boolean = true,
    val abs: AbsConfig = AbsConfig(),
) {
    val smbSources: List<SmbSource>
        get() = listOf(SmbSource(Source.SAMBA, MAIN_NAME, smb, includeInMain = true, lastScan = smbLastScan)) + smbExtra

    fun smbSource(id: String): SmbSource? = smbSources.firstOrNull { it.id == id }

    /** Source ids whose tracks make up the main library (queue "All", Randomize, default Library view). */
    val mergedSources: List<String>
        get() = smbSources.filter { it.includeInMain }.map { it.id } + if (deviceInMain) listOf(Source.LOCAL) else emptyList()

    /** Display name of any source id. */
    fun sourceName(id: String): String = if (id == Source.LOCAL) "This device" else smbSource(id)?.name ?: id

    companion object { const val MAIN_NAME = "Main library" }
}

class SettingsRepository(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val volume = floatPreferencesKey("volume")
        val whisper = booleanPreferencesKey("whisper")
        val shuffle = booleanPreferencesKey("shuffle")
        val repeat = stringPreferencesKey("repeat")
        val keepAwake = booleanPreferencesKey("keep_awake")
        val fullscreen = booleanPreferencesKey("fullscreen")
        val autoDim = intPreferencesKey("auto_dim_seconds")
        val levelTracks = booleanPreferencesKey("level_tracks")
        val levelOffset = floatPreferencesKey("level_offset_db")
        val gainScan = booleanPreferencesKey("gain_scan")
        val ramCache = booleanPreferencesKey("ram_cache")
        val mode = stringPreferencesKey("mode")
        val lastTrack = stringPreferencesKey("last_track")
        val smbLastScan = longPreferencesKey("smb_last_scan")
        val mediaStore = booleanPreferencesKey("media_store")
        val trees = stringSetPreferencesKey("local_trees")
        val deviceInMain = booleanPreferencesKey("device_in_main")
        val smbExtra = stringPreferencesKey("smb_extra_sources")
        val smbHost = stringPreferencesKey("smb_host")
        val smbShare = stringPreferencesKey("smb_share")
        val smbSub = stringPreferencesKey("smb_sub")
        val smbUser = stringPreferencesKey("smb_user")
        val smbDomain = stringPreferencesKey("smb_domain")
        val smbRescan = intPreferencesKey("smb_rescan")
        val absUrl = stringPreferencesKey("abs_url")
        val absUser = stringPreferencesKey("abs_user")
        val absPackage = stringPreferencesKey("abs_package")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            theme = ThemeId.fromName(p[K.theme]),
            volume = p[K.volume] ?: d.volume,
            whisper = p[K.whisper] ?: d.whisper,
            shuffle = p[K.shuffle] ?: d.shuffle,
            repeat = p[K.repeat]?.let { runCatching { RepeatMode.valueOf(it) }.getOrNull() } ?: d.repeat,
            keepAwake = p[K.keepAwake] ?: d.keepAwake,
            fullscreen = p[K.fullscreen] ?: d.fullscreen,
            autoDimSeconds = p[K.autoDim] ?: d.autoDimSeconds,
            levelTracks = p[K.levelTracks] ?: d.levelTracks,
            levelOffsetDb = p[K.levelOffset] ?: d.levelOffsetDb,
            gainScan = p[K.gainScan] ?: d.gainScan,
            ramCache = p[K.ramCache] ?: d.ramCache,
            mode = p[K.mode]?.let { runCatching { AppMode.valueOf(it) }.getOrNull() } ?: d.mode,
            lastTrackId = p[K.lastTrack] ?: "",
            smbLastScan = p[K.smbLastScan] ?: 0L,
            mediaStoreEnabled = p[K.mediaStore] ?: d.mediaStoreEnabled,
            localTreeUris = p[K.trees] ?: emptySet(),
            smb = SmbConfig(
                host = p[K.smbHost] ?: "",
                share = p[K.smbShare] ?: "",
                subfolder = p[K.smbSub] ?: "",
                username = p[K.smbUser] ?: "",
                domain = p[K.smbDomain] ?: d.smb.domain,
                autoRescanMinutes = p[K.smbRescan] ?: d.smb.autoRescanMinutes,
            ),
            smbExtra = decodeSources(p[K.smbExtra]),
            deviceInMain = p[K.deviceInMain] ?: d.deviceInMain,
            abs = AbsConfig(
                serverUrl = p[K.absUrl] ?: "",
                username = p[K.absUser] ?: "",
                appPackage = p[K.absPackage] ?: d.abs.appPackage,
            ),
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setTheme(v: ThemeId) = edit { it[K.theme] = v.name }
    suspend fun setVolume(v: Float) = edit { it[K.volume] = v }
    suspend fun setWhisper(v: Boolean) = edit { it[K.whisper] = v }
    suspend fun setShuffle(v: Boolean) = edit { it[K.shuffle] = v }
    suspend fun setRepeat(v: RepeatMode) = edit { it[K.repeat] = v.name }
    suspend fun setKeepAwake(v: Boolean) = edit { it[K.keepAwake] = v }
    suspend fun setFullscreen(v: Boolean) = edit { it[K.fullscreen] = v }
    suspend fun setAutoDimSeconds(v: Int) = edit { it[K.autoDim] = v }
    suspend fun setLevelTracks(v: Boolean) = edit { it[K.levelTracks] = v }
    suspend fun setLevelOffsetDb(v: Float) = edit { it[K.levelOffset] = v.coerceIn(GainModel.MIN_OFFSET_DB, GainModel.MAX_OFFSET_DB) }
    suspend fun setGainScan(v: Boolean) = edit { it[K.gainScan] = v }
    suspend fun setRamCache(v: Boolean) = edit { it[K.ramCache] = v }
    suspend fun setMode(v: AppMode) = edit { it[K.mode] = v.name }
    suspend fun setLastTrack(id: String) = edit { it[K.lastTrack] = id }
    suspend fun setSmbLastScan(at: Long) = edit { it[K.smbLastScan] = at }
    suspend fun setMediaStoreEnabled(v: Boolean) = edit { it[K.mediaStore] = v }
    suspend fun setLocalTrees(v: Set<String>) = edit { it[K.trees] = v }

    suspend fun setSmb(c: SmbConfig) = edit {
        it[K.smbHost] = c.host.trim()
        it[K.smbShare] = c.share.trim().trim('/', '\\')
        it[K.smbSub] = c.subfolder.trim().trim('/', '\\')
        it[K.smbUser] = c.username.trim()
        it[K.smbDomain] = c.domain.trim().ifEmpty { "WORKGROUP" }
        it[K.smbRescan] = c.autoRescanMinutes.coerceAtLeast(0)
    }

    suspend fun setDeviceInMain(v: Boolean) = edit { it[K.deviceInMain] = v }

    /** Adds or replaces an added share (never the main one), keeping the list order. */
    suspend fun upsertSmbSource(src: SmbSource) = edit {
        val list = decodeSources(it[K.smbExtra]).toMutableList()
        val clean = src.copy(name = src.name.trim().ifEmpty { "Share" }, config = normalized(src.config))
        val i = list.indexOfFirst { e -> e.id == src.id }
        if (i >= 0) list[i] = clean else list.add(clean)
        it[K.smbExtra] = encodeSources(list)
    }

    suspend fun removeSmbSource(id: String) = edit {
        it[K.smbExtra] = encodeSources(decodeSources(it[K.smbExtra]).filter { e -> e.id != id })
    }

    suspend fun setSmbSourceLastScan(id: String, at: Long) {
        if (id == Source.SAMBA) { setSmbLastScan(at); return }
        edit {
            it[K.smbExtra] = encodeSources(decodeSources(it[K.smbExtra]).map { e -> if (e.id == id) e.copy(lastScan = at) else e })
        }
    }

    private fun normalized(c: SmbConfig) = c.copy(
        host = c.host.trim(), share = c.share.trim().trim('/', '\\'), subfolder = c.subfolder.trim().trim('/', '\\'),
        username = c.username.trim(), domain = c.domain.trim().ifEmpty { "WORKGROUP" }, autoRescanMinutes = c.autoRescanMinutes.coerceAtLeast(0),
    )

    suspend fun setAbs(c: AbsConfig) = edit {
        it[K.absUrl] = c.serverUrl.trim().trimEnd('/')
        it[K.absUser] = c.username.trim()
        it[K.absPackage] = c.appPackage.trim().ifEmpty { "com.audiobookshelf.app" }
    }
}

internal fun encodeSources(list: List<SmbSource>): String = JSONArray().also { arr ->
    list.forEach { s ->
        arr.put(
            JSONObject()
                .put("id", s.id).put("name", s.name).put("host", s.config.host).put("share", s.config.share)
                .put("sub", s.config.subfolder).put("user", s.config.username).put("domain", s.config.domain)
                .put("rescan", s.config.autoRescanMinutes).put("main", s.includeInMain).put("last", s.lastScan),
        )
    }
}.toString()

internal fun decodeSources(json: String?): List<SmbSource> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SmbSource(
                id = o.getString("id"), name = o.optString("name", "Share"),
                config = SmbConfig(
                    host = o.optString("host"), share = o.optString("share"), subfolder = o.optString("sub"),
                    username = o.optString("user"), domain = o.optString("domain", "WORKGROUP"), autoRescanMinutes = o.optInt("rescan", 30),
                ),
                includeInMain = o.optBoolean("main", false), lastScan = o.optLong("last", 0),
            )
        }
    }.getOrDefault(emptyList())
}
