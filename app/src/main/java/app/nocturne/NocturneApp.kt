package app.nocturne

import android.app.Application
import androidx.media3.common.util.UnstableApi
import app.nocturne.abs.AbsRepository
import app.nocturne.data.NocturneDatabase
import app.nocturne.data.SecretStore
import app.nocturne.data.SettingsRepository
import app.nocturne.library.CoverLoader
import app.nocturne.library.LibraryManager
import app.nocturne.library.SmbProviders
import app.nocturne.playback.PlaybackEngine
import java.security.Security
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** Process-wide singletons. The app is a launcher, so this process is long-lived. */
@UnstableApi
class NocturneApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var db: NocturneDatabase; private set
    lateinit var settings: SettingsRepository; private set
    lateinit var secrets: SecretStore; private set
    /** One connection per Samba share, in three sets: scanning, tag reading and playback, so a big scan can never starve the player. */
    lateinit var smb: SmbProviders; private set
    lateinit var smbPlayback: SmbProviders; private set
    lateinit var smbEnrich: SmbProviders; private set
    lateinit var library: LibraryManager; private set
    lateinit var engine: PlaybackEngine; private set
    lateinit var covers: CoverLoader; private set
    lateinit var abs: AbsRepository; private set

    /** Latest settings, readable from non-suspending code (SMB provider callbacks). */
    @Volatile var latestSettings = app.nocturne.data.AppSettings()
        private set

    override fun onCreate() {
        super.onCreate()
        installBouncyCastle()
        db = NocturneDatabase.create(this)
        settings = SettingsRepository(this)
        secrets = SecretStore(this)
        latestSettings = runBlocking { settings.flow.first() }
        ioScope.launch {
            settings.flow.collect { latestSettings = it }
        }
        smb = SmbProviders({ latestSettings }, secrets::smbPasswordFor)
        smbPlayback = SmbProviders({ latestSettings }, secrets::smbPasswordFor)
        smbEnrich = SmbProviders({ latestSettings }, secrets::smbPasswordFor)
        library = LibraryManager(this, db.tracks(), settings, smb, smbEnrich, ioScope)
        covers = CoverLoader(this, smbPlayback)
        abs = AbsRepository(settings, secrets)
        engine = PlaybackEngine(this, db.tracks(), settings, smbPlayback, appScope)
        library.start()
    }

    /** smbj needs MD4/HMAC/AES-CMAC etc. that Android's trimmed-down "BC" provider no longer ships. */
    private fun installBouncyCastle() {
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
