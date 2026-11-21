package dev.phonefocus.infrastructure.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starts an installed app's launcher activity, as a home screen does. */
class AppLauncher(context: Context) {
    private val appContext = context.applicationContext

    /** Starts [packageName] in its own task; false when it has no launcher activity (anymore). */
    fun launch(packageName: String): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return try {
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("AppLauncher", "cannot launch $packageName", e)
            false
        } catch (e: SecurityException) {
            Log.w("AppLauncher", "not allowed to launch $packageName", e)
            false
        }
    }
}
