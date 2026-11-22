package dev.phonefocus.infrastructure.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * `POST_NOTIFICATIONS` is a runtime permission from Android 13 (API 33). Below that it does not
 * exist: notifications are allowed unless the user turned them off for the app.
 */
object NotificationPermission {
    /** Whether the permission is granted; always true below API 33, where it does not exist. */
    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
