package dev.phonefocus.infrastructure.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.phonefocus.R
import dev.phonefocus.application.FocusNotifier

/**
 * Posts the "Focus ended" notification when the exit countdown has run out (#10). Tapping it
 * opens the focus launcher, whose "Focus is off" window offers switching the home screen back
 * to One UI Home.
 *
 * Skipped silently when the user has not allowed notifications (`POST_NOTIFICATIONS` on Android
 * 13+, asked for when starting focus) or turned them off: the launcher still shows "Focus is off".
 */
class AndroidFocusNotifier(private val context: Context) : FocusNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun focusEnded() {
        try {
            if (!canPost()) {
                Log.i(TAG, "notifications not allowed; not posting Focus ended")
                return
            }
            ensureChannel()
            manager.notify(NOTIFICATION_ID, buildNotification())
        } catch (e: RuntimeException) {
            // Never let a notification problem break the unlock.
            Log.w(TAG, "could not post Focus ended", e)
        }
    }

    private fun canPost(): Boolean =
        NotificationPermission.isGranted(context) && manager.areNotificationsEnabled()

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Focus mode", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Tells you when focus mode has ended and the phone is unlocked."
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            // This app's own home activity, the focus launcher.
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_focus_ended)
            .setContentTitle("Focus ended")
            .setContentText("Your phone is unlocked. Tap to switch the home screen back to One UI Home.")
            .setStyle(
                Notification.BigTextStyle()
                    .bigText("Your phone is unlocked. Tap to switch the home screen back to One UI Home."),
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
    }

    private companion object {
        const val TAG = "AndroidFocusNotifier"
        const val CHANNEL_ID = "focus_mode"
        const val NOTIFICATION_ID = 10
    }
}
