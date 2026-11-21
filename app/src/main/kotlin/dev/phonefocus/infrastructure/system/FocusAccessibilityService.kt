package dev.phonefocus.infrastructure.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.core.util.forEach
import dev.phonefocus.application.AppBlockingContext
import dev.phonefocus.application.EvaluateForegroundApp
import dev.phonefocus.application.ForegroundAction
import dev.phonefocus.application.ForegroundScreen
import dev.phonefocus.application.ForegroundWindow
import dev.phonefocus.infrastructure.PhoneFocusApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * The app blocker (#11): on every window change, reads the application windows on screen and,
 * while focused, acts on blocked ones as [EvaluateForegroundApp] decides: bounce a full-screen
 * app back to the focus desktop, dismiss a picture-in-picture window.
 *
 * Only application windows are read (several in split screen). System windows (status bar,
 * notification shade, keyboard, overlays) are never judged; system packages that do show
 * application windows are never blocked either (see `NeverBlockedPackages`). Windows on other
 * displays (the Flip's cover screen) are read but not judged yet, see [ForegroundWindow].
 */
class FocusAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val evaluate = EvaluateForegroundApp()

    @Volatile
    private var context: AppBlockingContext? = null

    private val recheck = Runnable { check(event = null) }

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
        check(event)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(recheck)
        scope.cancel()
        super.onDestroy()
    }

    private fun check(event: AccessibilityEvent?) {
        val windows = applicationWindows()
        val screen = screenOf(windows, event)
        when (val action = evaluate(context, screen, SystemClock.elapsedRealtime())) {
            ForegroundAction.None -> Unit
            is ForegroundAction.Bounce -> bounce(action.packageName)
            is ForegroundAction.DismissPictureInPicture -> dismissPictureInPicture(action.packageName, windows)
            is ForegroundAction.RecheckIn -> recheckIn(action.delayMillis)
        }
    }

    /** The application windows on every display, with their package; empty when unreadable. */
    private fun applicationWindows(): List<Pair<AccessibilityWindowInfo, String>> = runCatching {
        buildList {
            windowsOnAllDisplays.forEach { _, displayWindows ->
                displayWindows
                    .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    .forEach { window -> window.root?.packageName?.toString()?.let { add(window to it) } }
            }
        }
    }.getOrElse {
        Log.w(TAG, "cannot read windows", it)
        emptyList()
    }

    /**
     * What is on screen. Falls back to the event's package (on its display) when no window can
     * be read, e.g. right after the service connects.
     */
    private fun screenOf(windows: List<Pair<AccessibilityWindowInfo, String>>, event: AccessibilityEvent?): ForegroundScreen {
        if (windows.isEmpty()) {
            val pkg = event?.packageName?.toString() ?: return ForegroundScreen(emptyList())
            return ForegroundScreen(listOf(ForegroundWindow(pkg, defaultDisplay = event.displayId == Display.DEFAULT_DISPLAY)))
        }
        val desktopInFront = windows.any { (window, pkg) ->
            pkg == packageName && window.displayId == Display.DEFAULT_DISPLAY && (window.isActive || window.isFocused)
        }
        return ForegroundScreen(
            windows.map { (window, pkg) ->
                ForegroundWindow(
                    packageName = pkg,
                    pictureInPicture = window.isInPictureInPictureMode,
                    defaultDisplay = window.displayId == Display.DEFAULT_DISPLAY,
                )
            },
            desktopInFront,
        )
    }

    private fun recheckIn(delayMillis: Long) {
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, delayMillis)
    }

    /** Back to the focus desktop, which shows the "Blocked during focus" notice. */
    private fun bounce(packageName: String) {
        Log.i(TAG, "blocked $packageName during focus")
        try {
            startActivity(desktopIntent(this.packageName).putExtra(EXTRA_BLOCKED, true))
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot start the focus desktop, going home instead", e)
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, BLOCKED_NOTICE, Toast.LENGTH_SHORT).show()
        }
        // Look again once the debounce is over, in case the app came straight back.
        recheckIn(EvaluateForegroundApp.DEFAULT_DEBOUNCE_MILLIS + RECHECK_SLACK_MILLIS)
    }

    /**
     * Closes the blocked app's picture-in-picture window. The system's PiP window offers a
     * "dismiss" accessibility action (the same as dragging it to the close target).
     */
    private fun dismissPictureInPicture(packageName: String, windows: List<Pair<AccessibilityWindowInfo, String>>) {
        val dismissed = windows
            .filter { (window, pkg) -> pkg == packageName && window.isInPictureInPictureMode }
            .map { (window, _) -> window.root?.performAction(AccessibilityNodeInfo.ACTION_DISMISS) == true }
            .any { it }
        Log.i(TAG, "blocked picture-in-picture of $packageName during focus (dismissed: $dismissed)")
        recheckIn(EvaluateForegroundApp.DEFAULT_DEBOUNCE_MILLIS + RECHECK_SLACK_MILLIS)
    }

    companion object {
        private const val TAG = "FocusAccessibility"
        private const val RECHECK_SLACK_MILLIS = 100L
        private const val BLOCKED_NOTICE = "Blocked during focus"

        /** The intent extra that tells the desktop an app was just blocked. */
        const val EXTRA_BLOCKED = "dev.phonefocus.extra.BLOCKED"

        /**
         * Starts this app's own home activity, the focus desktop, without depending on `ui`:
         * a `HOME` intent limited to this package resolves to it. Marked as not a user action,
         * so the app in front is not told the user is leaving it (no auto-enter
         * picture-in-picture).
         */
        fun desktopIntent(packageName: String): Intent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
