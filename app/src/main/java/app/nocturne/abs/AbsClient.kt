package app.nocturne.abs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

data class AbsBook(
    val id: String,
    val title: String,
    val author: String,
    /** 0..1 */
    val progress: Double,
    val durationSec: Double,
    val lastUpdateMs: Long,
) {
    val remainingSec: Double get() = ((1 - progress) * durationSec).coerceAtLeast(0.0)
}

class AbsAuthException(message: String) : IOException(message)

/** Minimal Audiobookshelf REST client: only what the "last played" widget needs (documented endpoints). */
class AbsClient(rawUrl: String) {
    val baseUrl: String = normalize(rawUrl)

    fun ping(): Boolean = try {
        val (code, _) = get("/ping", null, 4000)
        code == 200
    } catch (e: IOException) {
        false
    }

    /** Returns the API token. Newer servers may name it accessToken; both are accepted. */
    fun login(username: String, password: String): String {
        val body = JSONObject().put("username", username).put("password", password).toString()
        val conn = open("/login", 8000).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-return-tokens", "true")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code == 401 || code == 403) throw AbsAuthException("Wrong Audiobookshelf username or password.")
            if (code != 200) throw IOException("Audiobookshelf login failed (HTTP $code).")
            val user = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONObject("user")
            return user.optString("accessToken").ifEmpty { user.optString("token") }
                .ifEmpty { throw IOException("Audiobookshelf did not return a token.") }
        } finally {
            conn.disconnect()
        }
    }

    /** Most recently played, unfinished book, or null. */
    fun lastPlayed(token: String): AbsBook? {
        val me = JSONObject(getJson("/api/me", token))
        val progress = AbsParser.pickLastPlayed(me.optJSONArray("mediaProgress")) ?: return null
        val item = JSONObject(getJson("/api/items/${progress.itemId}?expanded=1", token))
        return AbsParser.toBook(progress, item)
    }

    fun cover(itemId: String, token: String): Bitmap? = try {
        val conn = open("/api/items/$itemId/cover?width=700", 8000).apply {
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (conn.responseCode != 200) null
            else conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    } catch (e: IOException) {
        null
    }

    private fun getJson(path: String, token: String): String {
        val (code, body) = get(path, token, 8000)
        if (code == 401 || code == 403) throw AbsAuthException("Audiobookshelf rejected the login token.")
        if (code != 200) throw IOException("Audiobookshelf returned HTTP $code.")
        return body
    }

    private fun get(path: String, token: String?, timeoutMs: Int): Pair<Int, String> {
        val conn = open(path, timeoutMs)
        if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader()?.readText() ?: "")
        } finally {
            conn.disconnect()
        }
    }

    private fun open(path: String, timeoutMs: Int): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }

    companion object {
        fun normalize(raw: String): String {
            val t = raw.trim().trimEnd('/')
            if (t.isEmpty()) return ""
            return if ("://" in t) t else "http://$t"
        }
    }
}

data class AbsProgress(
    val itemId: String,
    val progress: Double,
    val lastUpdate: Long,
    val currentTime: Double = 0.0,
    val duration: Double = 0.0,
)

object AbsParser {
    /**
     * Newest progress entry for a book (not a podcast episode) that is unfinished, has been started and has not
     * been hidden from "Continue listening" in Audiobookshelf.
     */
    fun pickLastPlayed(arr: JSONArray?): AbsProgress? {
        if (arr == null) return null
        var best: AbsProgress? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("isFinished", false) || o.optBoolean("hideFromContinueListening", false)) continue
            if (!o.isNull("episodeId") && o.optString("episodeId").isNotEmpty()) continue
            val progress = o.optDouble("progress", 0.0)
            val current = o.optDouble("currentTime", 0.0)
            if (progress <= 0.0 && current <= 0.0) continue
            val id = o.optString("libraryItemId")
            if (id.isEmpty()) continue
            val last = o.optLong("lastUpdate", 0L)
            if (best == null || last > best.lastUpdate) {
                best = AbsProgress(id, progress, last, current, o.optDouble("duration", 0.0))
            }
        }
        return best
    }

    fun toBook(p: AbsProgress, item: JSONObject): AbsBook {
        val media = item.optJSONObject("media")
        val meta = media?.optJSONObject("metadata")
        // Real servers: authorName on minified items, authors[] on expanded ones. Duration lives on the progress record.
        val author = meta?.optString("authorName").orEmpty().ifEmpty {
            val authors = meta?.optJSONArray("authors")
            (0 until (authors?.length() ?: 0)).mapNotNull { authors?.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotEmpty() } }
                .joinToString(", ")
        }
        val duration = maxOf(p.duration, media?.optDouble("duration", 0.0) ?: 0.0)
        val progress = if (duration > 0 && p.currentTime > 0) (p.currentTime / duration) else p.progress
        return AbsBook(
            id = p.itemId,
            title = meta?.optString("title").orEmpty().ifEmpty { "Unknown title" },
            author = author,
            progress = progress.coerceIn(0.0, 1.0),
            durationSec = duration,
            lastUpdateMs = p.lastUpdate,
        )
    }
}
