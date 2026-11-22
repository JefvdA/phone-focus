package dev.phonefocus.infrastructure.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
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
import dev.phonefocus.application.EvaluateShortVideoScreen
import dev.phonefocus.application.ForegroundAction
import dev.phonefocus.application.ForegroundScreen
import dev.phonefocus.application.ForegroundWindow
import dev.phonefocus.application.ScreenTreeDump
import dev.phonefocus.application.ShortVideoAction
import dev.phonefocus.domain.shortvideo.ShortVideoDetectors
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
 *
 * Short videos (#12): for apps whose rule is No short videos (while focused), the screen tree
 * of their window is matched against the short-video rules as [EvaluateShortVideoScreen]
 * decides, and a short-video screen is left with a global Back. Content changes are only
 * listened to while such an app is in the policy (or the debug tree dump is on), only acted on
 * for those apps, and coalesced to one inspection per [CONTENT_THROTTLE_MILLIS].
 */
class FocusAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val evaluate = EvaluateForegroundApp()
    private val evaluateShortVideos = EvaluateShortVideoScreen()
    private var screenTreeDebug: ScreenTreeDebug? = null

    @Volatile
    private var context: AppBlockingContext? = null

    /** Whether content-change events are currently subscribed to (see [updateEventTypes]). */
    private var listeningToContent = false
    private var lastDebugDumpMillis = 0L

    private val recheck = Runnable { check(event = null) }

    /** A short-video (re)inspection: coalesces content changes and rechecks after a Back. */
    private val inspect = Runnable { check(event = null) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val container = (application as PhoneFocusApp).container
        val debug = container.screenTreeDebug
        screenTreeDebug = debug
        scope.launch {
            container.observeAppBlocking()
                .catch { Log.e(TAG, "app blocking state failed", it) }
                .collect {
                    context = it
                    updateEventTypes()
                }
        }
        scope.launch { debug.enabled.collect { updateEventTypes() } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> check(event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> onContentChanged(event)
            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(recheck)
        handler.removeCallbacks(inspect)
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Scrolling a feed or swiping to a Reel fires content changes, not window-state changes.
     * Only those of inspected apps count, and they are coalesced into one inspection at most
     * every [CONTENT_THROTTLE_MILLIS].
     */
    private fun onContentChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val inspected = evaluateShortVideos.inspects(context, pkg)
        val dumped = screenTreeDebug?.enabled?.value == true && pkg != packageName
        if (!inspected && !dumped) return
        if (!handler.hasCallbacks(inspect)) handler.postDelayed(inspect, CONTENT_THROTTLE_MILLIS)
    }

    /**
     * Subscribes to content changes only while they are needed: an app with No short videos is
     * enforced now, or the debug tree dump is on. Otherwise only window-state changes arrive,
     * as configured in the service XML.
     */
    private fun updateEventTypes() {
        val wanted = context?.shortVideoBlockedPackages?.isNotEmpty() == true || screenTreeDebug?.enabled?.value == true
        if (wanted == listeningToContent) return
        val info = serviceInfo ?: return
        info.eventTypes = if (wanted) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        } else {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }
        serviceInfo = info
        listeningToContent = wanted
        Log.i(TAG, "listening to content changes: $wanted")
    }

    private fun check(event: AccessibilityEvent?) {
        val windows = applicationWindows()
        val screen = screenOf(windows, event)
        when (val action = evaluate(context, screen, SystemClock.elapsedRealtime())) {
            ForegroundAction.None -> checkShortVideos(windows)
            is ForegroundAction.Bounce -> bounce(action.packageName)
            is ForegroundAction.DismissPictureInPicture -> dismissPictureInPicture(action.packageName, windows)
            is ForegroundAction.RecheckIn -> {
                recheckIn(action.delayMillis)
                checkShortVideos(windows)
            }
        }
        dumpForDebug(windows)
    }

    /** Leaves a short-video screen of a full-screen app on the main display, see [EvaluateShortVideoScreen]. */
    private fun checkShortVideos(windows: List<AppWindow>) {
        val now = SystemClock.elapsedRealtime()
        for (window in windows) {
            if (window.window.displayId != Display.DEFAULT_DISPLAY || window.window.isInPictureInPictureMode) continue
            val action = evaluateShortVideos(context, window.packageName, now) {
                runCatching { AccessibilityScreenNode(window.root) }.getOrNull()
            }
            when (action) {
                ShortVideoAction.None -> continue
                is ShortVideoAction.GoBack -> goBack(action)
                is ShortVideoAction.ReturnToDesktop -> returnToDesktop(action)
                is ShortVideoAction.RecheckIn -> inspectIn(action.delayMillis)
            }
            return
        }
    }

    private fun goBack(action: ShortVideoAction.GoBack) {
        Log.i(TAG, "short video in ${action.packageName} (${action.match.rule.name}): back")
        performGlobalAction(GLOBAL_ACTION_BACK)
        Toast.makeText(this, SHORT_VIDEO_NOTICE, Toast.LENGTH_SHORT).show()
        // Look again once the debounce is over, in case Back did not leave the short video.
        inspectIn(EvaluateShortVideoScreen.DEFAULT_DEBOUNCE_MILLIS + RECHECK_SLACK_MILLIS)
    }

    private fun returnToDesktop(action: ShortVideoAction.ReturnToDesktop) {
        Log.i(TAG, "short video in ${action.packageName} (${action.match.rule.name}): back did not help, desktop")
        try {
            startActivity(desktopIntent(packageName))
        } catch (e: RuntimeException) {
            Log.w(TAG, "cannot start the focus desktop, going home instead", e)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        Toast.makeText(this, SHORT_VIDEO_NOTICE, Toast.LENGTH_SHORT).show()
        inspectIn(EvaluateShortVideoScreen.DEFAULT_DEBOUNCE_MILLIS + RECHECK_SLACK_MILLIS)
    }

    private fun inspectIn(delayMillis: Long) {
        handler.removeCallbacks(inspect)
        handler.postDelayed(inspect, delayMillis)
    }

    /**
     * Debug builds, tree dump on: logs the screen tree of the active app window on the main
     * display (not this app's) and keeps it for the debug screen, at most once per
     * [DEBUG_DUMP_THROTTLE_MILLIS].
     */
    private fun dumpForDebug(windows: List<AppWindow>) {
        val debug = screenTreeDebug ?: return
        if (!debug.enabled.value) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastDebugDumpMillis < DEBUG_DUMP_THROTTLE_MILLIS) return
        val target = windows.firstOrNull { (window, _, pkg) ->
            pkg != packageName && window.displayId == Display.DEFAULT_DISPLAY && (window.isActive || window.isFocused)
        } ?: return
        lastDebugDumpMillis = now
        runCatching {
            val node = AccessibilityScreenNode(target.root)
            val lines = ScreenTreeDump.render(node)
            val matched = ShortVideoDetectors.DEFAULT.detect(target.packageName, node)?.rule?.name
            Log.d(TREE_TAG, "=== ${target.packageName} (short video: ${matched ?: "no"}) ===")
            lines.forEach { Log.d(TREE_TAG, it) }
            debug.record(ScreenTreeSnapshot(target.packageName, lines, matched))
        }.onFailure { Log.w(TAG, "cannot dump the screen tree", it) }
    }

    /** An application window on screen, its root node and its package. */
    private data class AppWindow(
        val window: AccessibilityWindowInfo,
        val root: AccessibilityNodeInfo,
        val packageName: String,
    )

    /** The application windows on every display, with their package; empty when unreadable. */
    private fun applicationWindows(): List<AppWindow> = runCatching {
        buildList {
            windowsOnAllDisplays.forEach { _, displayWindows ->
                displayWindows
                    .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    .forEach { window ->
                        val root = window.root ?: return@forEach
                        root.packageName?.toString()?.let { add(AppWindow(window, root, it)) }
                    }
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
    private fun screenOf(windows: List<AppWindow>, event: AccessibilityEvent?): ForegroundScreen {
        if (windows.isEmpty()) {
            val pkg = event?.packageName?.toString() ?: return ForegroundScreen(emptyList())
            return ForegroundScreen(listOf(ForegroundWindow(pkg, defaultDisplay = isOnDefaultDisplay(event))))
        }
        val desktopInFront = windows.any { (window, _, pkg) ->
            pkg == packageName && window.displayId == Display.DEFAULT_DISPLAY && (window.isActive || window.isFocused)
        }
        return ForegroundScreen(
            windows.map { (window, _, pkg) ->
                ForegroundWindow(
                    packageName = pkg,
                    pictureInPicture = window.isInPictureInPictureMode,
                    defaultDisplay = window.displayId == Display.DEFAULT_DISPLAY,
                )
            },
            desktopInFront,
        )
    }

    /** The event's display is only known on API 33+; below, assume the main display. */
    private fun isOnDefaultDisplay(event: AccessibilityEvent): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || event.displayId == Display.DEFAULT_DISPLAY

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
    private fun dismissPictureInPicture(packageName: String, windows: List<AppWindow>) {
        val dismissed = windows
            .filter { (window, _, pkg) -> pkg == packageName && window.isInPictureInPictureMode }
            .map { (_, root, _) -> root.performAction(AccessibilityNodeInfo.ACTION_DISMISS) }
            .any { it }
        Log.i(TAG, "blocked picture-in-picture of $packageName during focus (dismissed: $dismissed)")
        recheckIn(EvaluateForegroundApp.DEFAULT_DEBOUNCE_MILLIS + RECHECK_SLACK_MILLIS)
    }

    companion object {
        private const val TAG = "FocusAccessibility"
        private const val RECHECK_SLACK_MILLIS = 100L
        private const val TREE_TAG = "FocusScreenTree"
        private const val BLOCKED_NOTICE = "Blocked during focus"
        private const val SHORT_VIDEO_NOTICE = "Short videos are blocked during focus"

        /** At most one short-video inspection per this many ms while an app's content changes. */
        private const val CONTENT_THROTTLE_MILLIS = 250L

        /** At most one debug tree dump per this many ms. */
        private const val DEBUG_DUMP_THROTTLE_MILLIS = 1_000L

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
