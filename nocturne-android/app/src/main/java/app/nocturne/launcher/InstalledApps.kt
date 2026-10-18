package app.nocturne.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings

data class AppEntry(val label: String, val packageName: String, val component: ComponentName, val icon: Drawable)

/** The bits of a home screen that are left to the OS: listing and starting other apps. */
object InstalledApps {
    fun list(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    icon = it.loadIcon(pm),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, app: AppEntry) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(i) }
    }

    /** Opens the system screen where the default home app is chosen. */
    fun openDefaultLauncherSettings(context: Context) {
        val i = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun openSystemSettings(context: Context) {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
