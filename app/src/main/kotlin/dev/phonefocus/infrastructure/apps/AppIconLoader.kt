package dev.phonefocus.infrastructure.apps

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * Loads app icons for an `InstalledApp`, whose package name is its icon reference. Kept out
 * of the domain and application layers, which never see Android graphics types.
 */
class AppIconLoader(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    /** The launcher icon of [packageName], or null when the app is gone. Call off the main thread. */
    fun load(packageName: String): Drawable? = try {
        packageManager.getApplicationIcon(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
