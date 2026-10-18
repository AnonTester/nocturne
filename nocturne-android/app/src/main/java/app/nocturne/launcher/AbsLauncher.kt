package app.nocturne.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
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
     * The ABS app cannot be told to play a book from outside: its media session rejects other apps, it has no play
     * intent or deep link, and it ignores media-button events until a book is already loaded. So "resume" opens the app
     * (where the book sits first under Continue Listening), or the item's page on the server without the app.
     */
    fun resume(context: Context, cfg: AbsConfig, book: AbsBook): String {
        val launch = context.packageManager.getLaunchIntentForPackage(cfg.appPackage)
        if (launch == null) {
            return if (cfg.serverUrl.isNotBlank()) {
                browse(context, "${cfg.serverUrl}/item/${book.id}"); "App not installed, opened the book on the server."
            } else "Audiobookshelf is not installed."
        }
        start(context, launch)
        return "Opened Audiobookshelf. Tap the book under Continue Listening to resume."
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
