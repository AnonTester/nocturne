package app.nocturne.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.nocturne.data.AbsConfig

/** Opens the Audiobookshelf app (or its web UI). ABS cannot be told to play anything from outside, see CLAUDE.md. */
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
