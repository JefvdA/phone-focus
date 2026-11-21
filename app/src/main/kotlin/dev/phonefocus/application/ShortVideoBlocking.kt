package dev.phonefocus.application

import dev.phonefocus.domain.shortvideo.ScreenNode
import dev.phonefocus.domain.shortvideo.ShortVideoDetectors
import dev.phonefocus.domain.shortvideo.ShortVideoMatch

/** What the app blocker does about a short-video screen, see [EvaluateShortVideoScreen]. */
sealed interface ShortVideoAction {
    /** Leave it: no short video, or the app's short videos are not blocked now. */
    data object None : ShortVideoAction

    /** Leave the short-video screen with a global Back and say why. */
    data class GoBack(val packageName: String, val match: ShortVideoMatch) : ShortVideoAction

    /**
     * Back did not get the user off the short-video screen several times in a row: send them
     * to the focus desktop instead (which always works, see [ForegroundAction.Bounce]).
     */
    data class ReturnToDesktop(val packageName: String, val match: ShortVideoMatch) : ShortVideoAction

    /** A short video is (still) on screen, but the app was just acted on: look again in [delayMillis]. */
    data class RecheckIn(val delayMillis: Long) : ShortVideoAction
}

/**
 * Decides what to do about the screen of an app in front (#12). Only apps whose
 * [ForegroundVerdict] is [ForegroundVerdict.ShortVideosBlocked] are inspected (focused or exit
 * pending, rule No short videos, not essential); for every other app, and while Unlocked,
 * nothing is done and the screen is not even read.
 *
 * When the app's detector recognises a short-video screen, the user is sent back, at most once
 * per [debounceMillis] per app: one Back fires a burst of window events, and without the
 * debounce each would press Back again and walk the user out of the app. A suppressed action
 * asks for a recheck when the debounce ends. When [maxBacks] Backs in a row did not clear the
 * screen (no check in between found it free of short videos), the next action returns to the
 * focus desktop instead.
 *
 * Holds the times of the last actions, so use one instance per service. Times are monotonic
 * milliseconds passed in by the caller.
 */
class EvaluateShortVideoScreen(
    private val detectors: ShortVideoDetectors = ShortVideoDetectors.DEFAULT,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val maxBacks: Int = DEFAULT_MAX_BACKS,
) {
    private val lastAction = mutableMapOf<String, Long>()
    private val backsInARow = mutableMapOf<String, Int>()

    /** Whether [packageName]'s screens need inspecting under [context] (cheap; no screen read). */
    fun inspects(context: AppBlockingContext?, packageName: String): Boolean =
        context != null && packageName in context.shortVideoBlockedPackages && packageName in detectors.packages

    /**
     * What to do now that [packageName] shows the screen [root] reads, at [nowMillis]. [root] is
     * only called when the app is inspected; it returns null when the screen cannot be read.
     */
    @Synchronized
    operator fun invoke(
        context: AppBlockingContext?,
        packageName: String,
        nowMillis: Long,
        root: () -> ScreenNode?,
    ): ShortVideoAction {
        if (!inspects(context, packageName)) {
            backsInARow.remove(packageName)
            return ShortVideoAction.None
        }
        val screen = root() ?: return ShortVideoAction.None
        val match = detectors.detect(packageName, screen)
        if (match == null) {
            backsInARow.remove(packageName)
            return ShortVideoAction.None
        }
        val elapsed = lastAction[packageName]?.let { nowMillis - it }
        if (elapsed != null && elapsed in 0 until debounceMillis) return ShortVideoAction.RecheckIn(debounceMillis - elapsed)
        lastAction[packageName] = nowMillis
        val backs = backsInARow.getOrDefault(packageName, 0)
        return if (backs >= maxBacks) {
            backsInARow.remove(packageName)
            ShortVideoAction.ReturnToDesktop(packageName, match)
        } else {
            backsInARow[packageName] = backs + 1
            ShortVideoAction.GoBack(packageName, match)
        }
    }

    companion object {
        /** Long enough for one Back to settle, short enough to close a Reel well within ~1s. */
        const val DEFAULT_DEBOUNCE_MILLIS = 700L

        /** Backs in a row that may fail to leave the short-video screen before going to the desktop. */
        const val DEFAULT_MAX_BACKS = 2
    }
}

/**
 * Renders a screen tree for the debug screen (#12): one line per node, indented by depth, with
 * class (simple name), view id, content description and flags. Used to find the identifiers of
 * a short-video screen after an app update. At most [maxNodes] nodes; long descriptions are cut.
 */
object ScreenTreeDump {
    const val DEFAULT_MAX_NODES: Int = 1_500
    private const val MAX_DESCRIPTION = 60

    fun render(root: ScreenNode, maxNodes: Int = DEFAULT_MAX_NODES): List<String> {
        val lines = mutableListOf<String>()
        val stack = ArrayDeque<Pair<ScreenNode, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            if (lines.size >= maxNodes) {
                lines += "… (truncated after $maxNodes nodes)"
                break
            }
            val (node, depth) = stack.removeLast()
            lines += describe(node, depth)
            for (i in node.childCount - 1 downTo 0) node.child(i)?.let { stack.addLast(it to depth + 1) }
        }
        return lines
    }

    private fun describe(node: ScreenNode, depth: Int): String = buildString {
        append("  ".repeat(depth))
        append(node.className?.substringAfterLast('.') ?: "?")
        node.viewId?.let { append(" id=").append(it) }
        node.contentDescription?.takeIf { it.isNotBlank() }?.let {
            val cut = if (it.length > MAX_DESCRIPTION) it.take(MAX_DESCRIPTION) + "…" else it
            append(" desc=\"").append(cut.replace('\n', ' ')).append('"')
        }
        if (node.isSelected) append(" [selected]")
        if (!node.isVisibleToUser) append(" [hidden]")
    }
}
