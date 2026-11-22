package dev.phonefocus.infrastructure.cover

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import android.telecom.TelecomManager
import android.util.Log
import dev.phonefocus.application.QuickAction
import dev.phonefocus.application.QuickActionResolver
import dev.phonefocus.infrastructure.system.queryActivities
import dev.phonefocus.infrastructure.system.resolveActivityInfo

/**
 * The apps behind the cover screen's quick buttons (#13), found with the standard intents
 * (made visible by the `<queries>` entries in the manifest):
 *
 * - Call: the default dialer ([TelecomManager]), else whatever handles `ACTION_DIAL`.
 * - Music: the default `CATEGORY_APP_MUSIC` app.
 * - Camera: the default still-image camera.
 * - Timer: the clock app's timers (`AlarmClock.ACTION_SHOW_TIMERS`).
 *
 * An action with no single default (the "choose an app" dialog) resolves to null.
 */
class AndroidQuickActions(context: Context) : QuickActionResolver {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    override fun packageFor(action: QuickAction): String? {
        if (action == QuickAction.Call) {
            appContext.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { return it }
        }
        val intent = queryIntent(action)
        val default = packageManager
            .resolveActivityInfo(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            // The resolver ("choose an app") dialog means there is no single default.
            ?.takeIf { it != "android" }
        if (default != null) return default
        // Music apps often declare APP_MUSIC on their launcher activity without CATEGORY_DEFAULT:
        // take the only one installed, if there is exactly one.
        return packageManager.queryActivities(intent)
            .map { it.activityInfo.packageName }
            .distinct()
            .singleOrNull()
    }

    /**
     * Opens [action] in [packageName] (the package that was checked against `AppAccess`, so the
     * system cannot pick another app) with [options], e.g. the cover display. False when the app
     * cannot be started.
     */
    fun launch(context: Context, action: QuickAction, packageName: String, options: Bundle?): Boolean {
        val intent = launchIntent(action, packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        return try {
            context.startActivity(intent, options)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "cannot open $action in $packageName", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "not allowed to open $action in $packageName", e)
            false
        }
    }

    private companion object {
        const val TAG = "AndroidQuickActions"

        /** The intent that finds the app for [action]. */
        fun queryIntent(action: QuickAction): Intent = when (action) {
            QuickAction.Call -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
            QuickAction.Music -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            QuickAction.Camera -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            QuickAction.Timer -> Intent(AlarmClock.ACTION_SHOW_TIMERS)
        }
    }

    /** The intent that opens [action] in exactly [packageName]. Music opens the app's launcher entry. */
    private fun launchIntent(action: QuickAction, packageName: String): Intent? = when (action) {
        QuickAction.Music -> packageManager.getLaunchIntentForPackage(packageName)
        else -> queryIntent(action).setPackage(packageName)
    }
}
