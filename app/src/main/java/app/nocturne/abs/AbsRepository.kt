package app.nocturne.abs

import android.graphics.Bitmap
import app.nocturne.data.SecretStore
import app.nocturne.data.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface AbsState {
    data object NotConfigured : AbsState
    data object Loading : AbsState
    /** Server unreachable or login failed: only "Open Audiobookshelf" is offered. */
    data class Unavailable(val reason: String) : AbsState
    /** Reachable; [book] is null when nothing was played recently. */
    data class Ready(val book: AbsBook?, val cover: Bitmap?) : AbsState
}

class AbsRepository(private val settings: SettingsRepository, private val secrets: SecretStore) {
    private val _state = MutableStateFlow<AbsState>(AbsState.NotConfigured)
    val state: StateFlow<AbsState> = _state.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val cfg = settings.flow.first().abs
        if (cfg.serverUrl.isBlank()) { _state.value = AbsState.NotConfigured; return@withContext }
        // Keep showing the last known book while refreshing; only show a spinner when there is nothing to show yet.
        if (_state.value !is AbsState.Ready) _state.value = AbsState.Loading
        val client = AbsClient(cfg.serverUrl)
        _state.value = try {
            if (!client.ping()) {
                AbsState.Unavailable("Audiobookshelf server not reachable at ${client.baseUrl}")
            } else if (cfg.username.isBlank() && secrets.absToken.isBlank()) {
                AbsState.Ready(null, null) // reachable, but no account to read progress from
            } else {
                val book = withToken(client, cfg.username) { client.lastPlayed(it) }
                AbsState.Ready(book, book?.let { b -> withToken(client, cfg.username) { client.cover(b.id, it) } })
            }
        } catch (e: AbsAuthException) {
            AbsState.Unavailable(e.message ?: "Audiobookshelf login failed.")
        } catch (e: IOException) {
            AbsState.Unavailable("Audiobookshelf error: ${e.message}")
        } catch (e: org.json.JSONException) {
            AbsState.Unavailable("Unexpected response from Audiobookshelf.")
        }
    }

    /** Runs [block] with the stored token; on rejection logs in again with the saved credentials and retries once. */
    private fun <T> withToken(client: AbsClient, username: String, block: (String) -> T): T {
        var token = secrets.absToken
        if (token.isBlank()) token = loginAndStore(client, username)
        return try {
            block(token)
        } catch (e: AbsAuthException) {
            if (username.isBlank() || secrets.absPassword.isBlank()) throw e
            block(loginAndStore(client, username))
        }
    }

    private fun loginAndStore(client: AbsClient, username: String): String {
        val password = secrets.absPassword
        if (username.isBlank() || password.isBlank()) throw AbsAuthException("Enter your Audiobookshelf username and password in Settings.")
        return client.login(username, password).also { secrets.absToken = it }
    }

    /** Settings screen: check server reachability and, if credentials are given, the login too. */
    suspend fun test(url: String, username: String, password: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val client = AbsClient(url)
        if (client.baseUrl.isEmpty()) return@withContext false to "Enter the server address first."
        try {
            if (!client.ping()) return@withContext false to "Server not reachable at ${client.baseUrl}"
            if (username.isBlank()) return@withContext true to "Server reachable (no account set, progress won't be shown)."
            val token = client.login(username, password.ifEmpty { secrets.absPassword })
            secrets.absToken = token
            true to "Connected and logged in as $username."
        } catch (e: AbsAuthException) {
            false to (e.message ?: "Login failed.")
        } catch (e: IOException) {
            false to "Error: ${e.message}"
        } catch (e: org.json.JSONException) {
            false to "Unexpected response from the server."
        }
    }
}
