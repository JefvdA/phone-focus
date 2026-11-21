package dev.phonefocus.infrastructure.cover

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import dev.phonefocus.R
import dev.phonefocus.application.CoverScreen
import dev.phonefocus.application.ObserveCoverScreen
import dev.phonefocus.application.QuickAction
import dev.phonefocus.infrastructure.PhoneFocusApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Draws the retro cover-screen widget (#13) with [RemoteViews] and keeps it current.
 *
 * [refresh] is called when the focus session changes (observed in the app process, see
 * [PhoneFocusApp]), after every `tick` from boot or the unlock alarm, when the widget is added
 * and while the exit countdown runs: then a non-wakeup alarm refreshes it exactly when the shown
 * minute goes down ([CoverScreen.nextRefreshIn]), so the countdown never shows less time than is
 * left and the phone is never woken up for it.
 */
class CoverWidget(context: Context, private val coverScreen: ObserveCoverScreen) {
    private val appContext = context.applicationContext
    private val widgets = AppWidgetManager.getInstance(appContext)
    private val alarms = appContext.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    /** Redraws every placed widget and re-schedules the countdown refresh; nothing when none is placed. */
    suspend fun refresh() = mutex.withLock {
        val ids = widgets.getAppWidgetIds(ComponentName(appContext, CoverWidgetProvider::class.java))
        if (ids.isEmpty()) {
            cancelRefresh()
            return@withLock
        }
        val screen = coverScreen.current()
        widgets.updateAppWidget(ids, views(screen))
        val next = screen.nextRefreshIn
        if (next != null) scheduleRefreshIn(next.toMillis()) else cancelRefresh()
    }

    /** Stops the countdown refresh (the last widget was removed). */
    fun cancelRefresh() {
        alarms.cancel(refreshIntent())
    }

    private fun views(screen: CoverScreen): RemoteViews =
        RemoteViews(appContext.packageName, R.layout.cover_widget).apply {
            setTextViewText(R.id.cover_status, screen.status.text)
            val openCover = activityIntent(REQUEST_OPEN, CoverIntents.open(appContext))
            setOnClickPendingIntent(R.id.cover_window, openCover)
            screen.buttons.forEach { button ->
                val id = buttonId(button.action)
                setTextViewText(id, button.action.label)
                setTextColor(id, appContext.getColor(if (button.enabled) R.color.retro_text else R.color.retro_disabled_text))
                setInt(id, "setBackgroundResource", if (button.enabled) R.drawable.retro_button_raised else R.drawable.retro_button_disabled)
                setContentDescription(id, if (button.enabled) button.action.label else "${button.action.label}, blocked")
                // Every tap is checked again by the cover activity, so a stale widget can't open a blocked app.
                val quick = activityIntent(REQUEST_ACTION_BASE + button.action.ordinal, CoverIntents.quickAction(appContext, button.action))
                setOnClickPendingIntent(id, quick)
            }
        }

    /** A widget tap that opens the cover activity on the cover screen (a plain activity: no trampoline). */
    private fun activityIntent(requestCode: Int, intent: Intent): PendingIntent = PendingIntent.getActivity(
        appContext,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        CoverDisplay.launchOptions(appContext),
    )

    private fun scheduleRefreshIn(delayMillis: Long) {
        val triggerAt = SystemClock.elapsedRealtime() + delayMillis
        val intent = refreshIntent()
        // ELAPSED_REALTIME (not _WAKEUP): delivered when the phone is awake anyway, e.g. when
        // the cover screen is on; a sleeping phone gets the update on wake-up.
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExact(AlarmManager.ELAPSED_REALTIME, triggerAt, intent)
        } else {
            alarms.setWindow(AlarmManager.ELAPSED_REALTIME, triggerAt, INEXACT_WINDOW_MILLIS, intent)
        }
    }

    private fun refreshIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(appContext, CoverWidgetProvider::class.java).setAction(CoverWidgetProvider.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_OPEN = 0
        const val REQUEST_ACTION_BASE = 1

        /** Without exact alarms the minute may update up to this late: showing more time, never less. */
        const val INEXACT_WINDOW_MILLIS = 30_000L

        fun buttonId(action: QuickAction): Int = when (action) {
            QuickAction.Call -> R.id.cover_button_call
            QuickAction.Music -> R.id.cover_button_music
            QuickAction.Camera -> R.id.cover_button_camera
            QuickAction.Timer -> R.id.cover_button_timer
        }
    }
}

/**
 * The cover-screen widget (#13), registered for Samsung's Flex Window through the
 * `com.samsung.android.appwidget.provider` meta-data (`display="sub_screen"`).
 */
class CoverWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refreshAsync(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context)
    }

    override fun onDisabled(context: Context) {
        (context.applicationContext as PhoneFocusApp).container.coverWidget.cancelRefresh()
    }

    private fun refreshAsync(context: Context) {
        val container = (context.applicationContext as PhoneFocusApp).container
        val pending = goAsync()
        container.scope.launch {
            try {
                container.coverWidget.refresh()
            } catch (e: Exception) {
                Log.e(TAG, "cover widget refresh failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "CoverWidgetProvider"
        const val ACTION_REFRESH = "dev.phonefocus.action.COVER_WIDGET_REFRESH"
    }
}

/** Intents that open the cover activity (declared with [ACTION_COVER_SCREEN] in the manifest). */
object CoverIntents {
    const val ACTION_COVER_SCREEN = "dev.phonefocus.action.COVER_SCREEN"
    const val EXTRA_QUICK_ACTION = "dev.phonefocus.extra.QUICK_ACTION"

    /** The cover activity with its status and quick buttons. */
    fun open(context: Context): Intent =
        Intent(ACTION_COVER_SCREEN)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** The cover activity, opening [action] right away when it is allowed. */
    fun quickAction(context: Context, action: QuickAction): Intent =
        open(context).putExtra(EXTRA_QUICK_ACTION, action.name)

    /** The quick action [intent] asks for, if any. */
    fun quickActionOf(intent: Intent?): QuickAction? =
        intent?.getStringExtra(EXTRA_QUICK_ACTION)?.let { name -> QuickAction.entries.firstOrNull { it.name == name } }
}
