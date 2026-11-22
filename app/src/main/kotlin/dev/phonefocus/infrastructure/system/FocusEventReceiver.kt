package dev.phonefocus.infrastructure.system

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.phonefocus.infrastructure.PhoneFocusApp
import kotlinx.coroutines.launch

/**
 * Runs `tick` (unlock if due, otherwise refresh the checkpoint and re-schedule the unlock)
 * on system events: boot, app update, exact alarms allowed again (Android 12) and the unlock
 * alarm (declared in the manifest), plus screen-on, user-present and shutdown (registered at
 * runtime by [PhoneFocusApp], because Android only delivers those to runtime receivers).
 */
class FocusEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val app = context.applicationContext as PhoneFocusApp
        val pending = goAsync()
        app.container.scope.launch {
            try {
                app.container.commands.tick()
                // Before finish(): the process may be gone soon after (e.g. the unlock alarm).
                app.container.coverWidget.refresh()
            } catch (e: Exception) {
                Log.e(TAG, "tick failed for ${intent.action}", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "FocusEventReceiver"
        const val ACTION_UNLOCK_ALARM = "dev.phonefocus.action.UNLOCK_ALARM"

        /** Actions only delivered to receivers registered at runtime. */
        val RUNTIME_ACTIONS = listOf(
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_SHUTDOWN,
        )

        private val HANDLED_ACTIONS = RUNTIME_ACTIONS + listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Android 12: SCHEDULE_EXACT_ALARM granted again (revoking it cancels exact alarms).
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            ACTION_UNLOCK_ALARM,
        )
    }
}
