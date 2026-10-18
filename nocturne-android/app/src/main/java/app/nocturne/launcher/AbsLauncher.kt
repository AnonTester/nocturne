package app.nocturne.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import app.nocturne.abs.AbsBook
import app.nocturne.data.AbsConfig

/** Hands control to the Audiobookshelf app (or its web UI). ABS has no documented "play this item" intent. */
object AbsLauncher {
    fun isAppInstalled(context: Context, cfg: AbsConfig): Boolean =
        context.packageManager.getLaunchIntentForPackage(cfg.appPackage) != null

    /** Opens ABS' own UI so the user can pick anything. Falls back to the web app. */
    fun open(context: Context, cfg: AbsConfig): String {
        val launch = context.packageManager.getLaunchIntentForPackage(cfg.appPackage)
        return when {
            launch != null -> { start(context, launch); "Opened Audiobookshelf." }
            cfg.serverUrl.isNotBlank() -> { browse(context, cfg.serverUrl); "App not installed, opened the web player." }
            else -> "Audiobookshelf is not installed and no server address is set."
        }
    }

    /**
     * Best effort resume: tell ABS' media session to play (works if its session is alive), then bring the app
     * forward. Without the app, open the item's page on the server.
     */
    fun resume(context: Context, cfg: AbsConfig, book: AbsBook): String {
        val launch = context.packageManager.getLaunchIntentForPackage(cfg.appPackage)
        if (launch == null) {
            return if (cfg.serverUrl.isNotBlank()) {
                browse(context, "${cfg.serverUrl}/item/${book.id}"); "App not installed, opened the book on the server."
            } else "Audiobookshelf is not installed."
        }
        sendPlayKey(context, cfg.appPackage)
        start(context, launch)
        return "Resuming in Audiobookshelf."
    }

    private fun sendPlayKey(context: Context, pkg: String) {
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(now, now, action, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
            runCatching { context.sendBroadcast(intent) }
        }
    }

    private fun start(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun browse(context: Context, url: String) {
        val u = if ("://" in url) url else "http://$url"
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
