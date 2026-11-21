package dev.phonefocus.infrastructure.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import dev.phonefocus.application.AppBlockingContext
import dev.phonefocus.application.EvaluateForegroundApp
import dev.phonefocus.application.ForegroundAction
import dev.phonefocus.infrastructure.PhoneFocusApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * The app blocker (#11): on every window change, finds the apps in front and, while focused,
 * sends the user back to the focus desktop when one is blocked ([EvaluateForegroundApp]).
 *
 * The apps in front are the packages of the on-screen application windows (several in split
 * screen or pop-up view). System windows (status bar, notification shade, keyboard, overlays)
 * are not application windows and are never judged; system packages that do show application
 * windows are never blocked either (see `NeverBlockedPackages`).
 */
class FocusAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val evaluate = EvaluateForegroundApp()

    @Volatile
    private var context: AppBlockingContext? = null

    private val recheck = Runnable { check(eventPackage = null) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as PhoneFocusApp).container
        scope.launch {
            container.observeAppBlocking()
                .catch { Log.e(TAG, "app blocking state failed", it) }
                .collect { context = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        check(event.packageName?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(recheck)
        scope.cancel()
        super.onDestroy()
    }

    private fun check(eventPackage: String?) {
        val packages = foregroundPackages(eventPackage)
        when (val action = evaluate(context, packages, SystemClock.elapsedRealtime())) {
            ForegroundAction.None -> Unit
            is ForegroundAction.Bounce -> bounce(action.packageName)
            is ForegroundAction.RecheckIn -> {
                handler.removeCallbacks(recheck)
                handler.postDelayed(recheck, action.delayMillis)
            }
        }
    }

    /**
     * Packages of the application windows on screen. Falls back to the event's package when
     * the window list is not available (e.g. right after the service connects).
     */
    private fun foregroundPackages(eventPackage: String?): Set<String> {
        val fromWindows = runCatching {
            windows
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNullTo(LinkedHashSet()) { it.root?.packageName?.toString() }
        }.getOrElse {
            Log.w(TAG, "cannot read windows", it)
            emptySet()
        }
        return fromWindows.ifEmpty { setOfNotNull(eventPackage) }
    }

    /** Back to the focus desktop, which shows the "Blocked during focus" notice. */
    private fun bounce(packageName: String) {
        Log.i(TAG, "blocked $packageName during focus")
        handler.removeCallbacks(recheck)
        try {
            startActivity(desktopIntent(packageName = this.packageName).putExtra(EXTRA_BLOCKED, true))
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot start the focus desktop, going home instead", e)
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, BLOCKED_NOTICE, Toast.LENGTH_SHORT).show()
        }
        // Look again once the debounce is over, in case the app came straight back.
        handler.postDelayed(recheck, EvaluateForegroundApp.DEFAULT_DEBOUNCE_MILLIS + RECHECK_SLACK_MILLIS)
    }

    companion object {
        private const val TAG = "FocusAccessibility"
        private const val RECHECK_SLACK_MILLIS = 100L
        const val BLOCKED_NOTICE = "Blocked during focus"

        /** The intent extra that tells the desktop an app was just blocked. */
        const val EXTRA_BLOCKED = "dev.phonefocus.extra.BLOCKED"

        /**
         * Starts this app's own home activity, the focus desktop, without depending on `ui`:
         * a `HOME` intent limited to this package resolves to it.
         */
        fun desktopIntent(packageName: String): Intent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
