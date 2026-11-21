package dev.phonefocus.domain

/**
 * Domain service: what the user may do with an app right now, given the [session] and the
 * device's [essentialApps]. Launcher (#8) and accessibility service enforce this rather than
 * reading the policy directly, so an essential app is never blocked even if the policy lists
 * it as [AppRule.Blocked] (or does not list it, which also means Blocked).
 *
 * This only relaxes enforcement for essential apps; the session and its invariants are
 * untouched and every other package keeps the policy's rule (unlisted = Blocked).
 */
class AppAccess(private val session: FocusSession, private val essentialApps: EssentialApps) {
    /** Whether [packageName] may be launched now. */
    fun canLaunch(packageName: String): Boolean =
        packageName in essentialApps || session.isLaunchAllowed(packageName)

    /** Whether the short-video feed of [packageName] must be blocked now. Never for essential apps. */
    fun blocksShortVideos(packageName: String): Boolean =
        packageName !in essentialApps && session.isShortVideoBlocked(packageName)

    /** The packages the focus home screen offers: the launchable ones plus the essential apps. */
    fun allowedApps(): Set<String> =
        if (session.state.isFocusActive) session.allowedApps() + essentialApps.packages else emptySet()
}
