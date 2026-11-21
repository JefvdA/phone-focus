package dev.phonefocus.infrastructure.time

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dev.phonefocus.application.UnlockScheduler
import dev.phonefocus.infrastructure.system.FocusEventReceiver
import java.time.Duration

/**
 * Schedules the unlock with an [AlarmManager] alarm on the monotonic clock
 * (`ELAPSED_REALTIME_WAKEUP`), so changing the system time does not move it.
 *
 * Uses an exact alarm when allowed (`USE_EXACT_ALARM` is granted at install). If exact alarms
 * are not allowed it falls back to an inexact one: the unlock may then come a few minutes late,
 * never early, and the app also ticks on app start, boot and screen-on.
 */
class AlarmUnlockScheduler(private val context: Context) : UnlockScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun scheduleUnlockIn(delay: Duration) {
        val triggerAt = SystemClock.elapsedRealtime() + delay.toMillis()
        val intent = pendingIntent()
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, intent)
        }
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, FocusEventReceiver::class.java).setAction(FocusEventReceiver.ACTION_UNLOCK_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
