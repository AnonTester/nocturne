package app.nocturne.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Credentials (SMB password, Audiobookshelf password/token) live here, encrypted at rest. */
class SecretStore(context: Context) {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "nocturne_secrets", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Keystore unavailable/corrupt: fall back to a private plain store rather than crash the launcher.
        context.getSharedPreferences("nocturne_secrets_fallback", Context.MODE_PRIVATE)
    }

    var smbPassword: String
        get() = prefs.getString(SMB_PASSWORD, "") ?: ""
        set(v) = prefs.edit().putString(SMB_PASSWORD, v).apply()

    /** Password of any Samba share: the main one keeps its original key, added shares get one key each. */
    fun smbPasswordFor(sourceId: String): String =
        if (sourceId == Source.SAMBA) smbPassword else prefs.getString(SMB_PASSWORD + "_" + sourceId, "") ?: ""

    fun setSmbPasswordFor(sourceId: String, value: String) {
        if (sourceId == Source.SAMBA) smbPassword = value else prefs.edit().putString(SMB_PASSWORD + "_" + sourceId, value).apply()
    }

    fun removeSmbPassword(sourceId: String) {
        if (sourceId != Source.SAMBA) prefs.edit().remove(SMB_PASSWORD + "_" + sourceId).apply()
    }

    var absPassword: String
        get() = prefs.getString(ABS_PASSWORD, "") ?: ""
        set(v) = prefs.edit().putString(ABS_PASSWORD, v).apply()

    var absToken: String
        get() = prefs.getString(ABS_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(ABS_TOKEN, v).apply()

    private companion object {
        const val SMB_PASSWORD = "smb_password"
        const val ABS_PASSWORD = "abs_password"
        const val ABS_TOKEN = "abs_token"
    }
}
