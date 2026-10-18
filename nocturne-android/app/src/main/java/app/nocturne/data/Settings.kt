package app.nocturne.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.nocturne.ui.theme.ThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    val mode: AppMode = AppMode.MUSIC,
    val lastTrackId: String = "",
    val smbLastScan: Long = 0,
    val mediaStoreEnabled: Boolean = true,
    val localTreeUris: Set<String> = emptySet(),
    val smb: SmbConfig = SmbConfig(),
    val abs: AbsConfig = AbsConfig(),
)

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
        val mode = stringPreferencesKey("mode")
        val lastTrack = stringPreferencesKey("last_track")
        val smbLastScan = longPreferencesKey("smb_last_scan")
        val mediaStore = booleanPreferencesKey("media_store")
        val trees = stringSetPreferencesKey("local_trees")
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

    suspend fun setAbs(c: AbsConfig) = edit {
        it[K.absUrl] = c.serverUrl.trim().trimEnd('/')
        it[K.absUser] = c.username.trim()
        it[K.absPackage] = c.appPackage.trim().ifEmpty { "com.audiobookshelf.app" }
    }
}
