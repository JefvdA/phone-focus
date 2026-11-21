package dev.phonefocus.application

import dev.phonefocus.domain.AppAccess
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.NeverBlockedPackages
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Device-specific packages the app blocker must never send away from (#11), on top of the fixed
 * [NeverBlockedPackages]: the enabled keyboards, the home/recents providers and the dialer and
 * in-call apps of this device. Implemented in `infrastructure`.
 */
interface SystemPackagesSource {
    /** The current packages, re-emitted when they may have changed (e.g. a new keyboard). */
    val systemPackages: Flow<Set<String>>
}

/** Why an app in front may stay or must be left, see [AppBlockingContext.verdict]. */
enum class ForegroundVerdict(val bounces: Boolean) {
    /** No focus session is active: nothing is blocked. */
    Unlocked(false),

    /** A system package that must never be blocked (calls, system UI, keyboard, ...). */
    NeverBlocked(false),

    /** [AppAccess] lets it be used now: an Allowed / No short videos app or an essential app. */
    Allowed(false),

    /** No launcher entry: not an app the user can pick in the catalogue (system component, overlay). */
    NotAnApp(false),

    /** A launchable app that is Blocked, or unlisted (new apps are Blocked): send the user home. */
    Blocked(true),
}

/** Everything the app blocker needs to judge the app in front, as one consistent snapshot. */
data class AppBlockingContext(
    val session: FocusSession,
    val essentialApps: EssentialApps,
    /** Device-specific never-blocked packages ([SystemPackagesSource]). */
    val systemPackages: Set<String>,
    /** The packages with a launcher entry, i.e. the apps of the catalogue. */
    val launchablePackages: Set<String>,
) {
    /**
     * Whether [packageName], now in front, may stay. Nothing is blocked while Unlocked; system
     * packages never are; otherwise [AppAccess] decides for every app with a launcher entry,
     * so unlisted and newly installed apps are blocked like Blocked ones.
     */
    fun verdict(packageName: String): ForegroundVerdict = when {
        !session.state.isFocusActive -> ForegroundVerdict.Unlocked
        packageName in NeverBlockedPackages || packageName in systemPackages -> ForegroundVerdict.NeverBlocked
        AppAccess(session, essentialApps).canLaunch(packageName) -> ForegroundVerdict.Allowed
        packageName !in launchablePackages -> ForegroundVerdict.NotAnApp
        else -> ForegroundVerdict.Blocked
    }
}

/** The [AppBlockingContext], updated when the session, the essential or system apps or the installed apps change. */
class ObserveAppBlocking(
    private val sessions: ObserveFocusSession,
    private val essentialApps: EssentialAppsSource,
    private val systemPackages: SystemPackagesSource,
    private val installedApps: InstalledAppsSource,
) {
    operator fun invoke(): Flow<AppBlockingContext> =
        combine(
            sessions(),
            essentialApps.essentialApps,
            systemPackages.systemPackages,
            installedApps.apps.map { apps -> apps.mapTo(HashSet()) { it.packageName } },
        ) { session, essential, system, launchable ->
            AppBlockingContext(session, essential, system, launchable)
        }.distinctUntilChanged()
}

/** What the app blocker does about the apps in front, see [EvaluateForegroundApp]. */
sealed interface ForegroundAction {
    /** Leave it. */
    data object None : ForegroundAction

    /** Send the user back to the focus desktop and say why. */
    data class Bounce(val packageName: String) : ForegroundAction

    /**
     * A bounce for the same app was just sent: don't send another yet, but look at the screen
     * again in [delayMillis], in case the app is still (or again) in front.
     */
    data class RecheckIn(val delayMillis: Long) : ForegroundAction
}

/**
 * Decides what to do when the apps in front change (#11): bounce a [ForegroundVerdict.Blocked]
 * one back to the focus desktop, at most once per [debounceMillis] per app.
 *
 * Opening an app fires a burst of window events; without the debounce each would start the
 * desktop again and show another notice. A suppressed bounce asks for a recheck when the
 * debounce ends, so an app that is still or again in front is bounced then (well within ~1s)
 * instead of staying open until the next window event.
 *
 * Holds the time of the last bounce, so use one instance per service. Times are monotonic
 * milliseconds passed in by the caller.
 */
class EvaluateForegroundApp(private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS) {
    private var lastBounce: Pair<String, Long>? = null

    /**
     * What to do now that [packages] are in front (several in split screen or pop-up view), at
     * [nowMillis]. While [context] is not known yet (just started) nothing can be judged, so a
     * recheck is asked for.
     */
    @Synchronized
    operator fun invoke(context: AppBlockingContext?, packages: Collection<String>, nowMillis: Long): ForegroundAction {
        if (context == null) return if (packages.isEmpty()) ForegroundAction.None else ForegroundAction.RecheckIn(debounceMillis)
        val blocked = packages.firstOrNull { context.verdict(it).bounces } ?: return ForegroundAction.None
        val last = lastBounce
        if (last != null && last.first == blocked) {
            val elapsed = nowMillis - last.second
            if (elapsed in 0 until debounceMillis) return ForegroundAction.RecheckIn(debounceMillis - elapsed)
        }
        lastBounce = blocked to nowMillis
        return ForegroundAction.Bounce(blocked)
    }

    companion object {
        /** Short enough that a recheck still bounces within ~1s of an app (re)appearing. */
        const val DEFAULT_DEBOUNCE_MILLIS = 500L
    }
}

/**
 * Whether the accessibility service that blocks apps is set up ([StartPrerequisite.AccessibilityService]).
 * Implemented in `infrastructure`; the user can turn it off at any time in v1.
 */
interface AppBlockerStatusSource {
    /** The current status, re-emitted when the enabled accessibility services change. */
    val status: Flow<PrerequisiteStatus>

    /** Checks again now, e.g. when the desktop comes back on screen. */
    fun refresh()
}

/**
 * Whether the desktop must warn that apps are not blocked (#11): focus is on but the
 * accessibility service is off. Never while Unlocked or when this build has no service.
 */
class ObserveAppBlockerWarning(
    private val sessions: ObserveFocusSession,
    private val blocker: AppBlockerStatusSource,
) {
    operator fun invoke(): Flow<Boolean> =
        combine(sessions(), blocker.status) { session, status ->
            session.state.isFocusActive && status == PrerequisiteStatus.Missing
        }.distinctUntilChanged()
}
