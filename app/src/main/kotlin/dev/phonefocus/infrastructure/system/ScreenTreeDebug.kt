package dev.phonefocus.infrastructure.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The last screen tree seen of [packageName], rendered by `ScreenTreeDump`, and what it matched. */
data class ScreenTreeSnapshot(
    val packageName: String,
    val lines: List<String>,
    /** The short-video rule that matched this screen, or null. */
    val matchedRule: String?,
)

/**
 * Debug builds only (#12): when [enabled], the app blocker dumps the screen tree of the app in
 * front to logcat (tag `FocusScreenTree`) and keeps the latest one per app here, for the debug
 * screen. Used to find the view ids of a short-video screen after an app update. In memory only.
 */
class ScreenTreeDebug(val available: Boolean) {
    private val enabledState = MutableStateFlow(false)
    private val snapshotsState = MutableStateFlow<Map<String, ScreenTreeSnapshot>>(emptyMap())

    val enabled: StateFlow<Boolean> = enabledState.asStateFlow()

    /** The latest snapshot per package, most recent last. */
    val snapshots: StateFlow<Map<String, ScreenTreeSnapshot>> = snapshotsState.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        enabledState.value = enabled && available
    }

    fun record(snapshot: ScreenTreeSnapshot) {
        if (!enabledState.value) return
        snapshotsState.update { (it - snapshot.packageName) + (snapshot.packageName to snapshot) }
    }

    fun clear() {
        snapshotsState.value = emptyMap()
    }
}
