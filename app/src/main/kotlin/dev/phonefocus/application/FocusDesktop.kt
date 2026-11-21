package dev.phonefocus.application

import dev.phonefocus.domain.AppAccess
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.FocusSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/** What the focus launcher (#8) shows: the [session] and the [apps] on the desktop. */
data class FocusDesktop(
    val session: FocusSession,
    /** The desktop icons, sorted by label. Empty while Unlocked. */
    val apps: List<InstalledApp>,
) {
    companion object {
        /**
         * The desktop for [session]: every installed app that [AppAccess] offers on the focus home
         * screen (Allowed and No short videos apps, plus the essential apps Settings and the
         * dialer), except [ownPackage]: this app is the desktop itself. Nothing while Unlocked.
         */
        fun of(
            session: FocusSession,
            installed: List<InstalledApp>,
            essentialApps: EssentialApps,
            ownPackage: String,
        ): FocusDesktop {
            val offered = AppAccess(session, essentialApps).allowedApps()
            val apps = installed
                .distinctBy { it.packageName }
                .filter { it.packageName in offered && it.packageName != ownPackage }
                .sortedWith(BY_LABEL)
            return FocusDesktop(session, apps)
        }

        private val BY_LABEL: Comparator<InstalledApp> =
            compareBy(String.CASE_INSENSITIVE_ORDER) { app: InstalledApp -> app.label }
                .thenBy { it.packageName }
    }
}

/**
 * The focus launcher's desktop ([FocusDesktop]), updated when the session changes or apps are
 * installed, removed or updated.
 */
class ObserveFocusDesktop(
    private val sessions: ObserveFocusSession,
    private val installedApps: InstalledAppsSource,
    private val essentialApps: EssentialAppsSource,
    private val ownPackage: String,
) {
    operator fun invoke(): Flow<FocusDesktop> =
        combine(sessions(), installedApps.apps, essentialApps.essentialApps) { session, installed, essential ->
            FocusDesktop.of(session, installed, essential, ownPackage)
        }.distinctUntilChanged()
}

/**
 * Whether an app may be launched right now, through [AppAccess]. The launcher checks this on
 * every tap, so an icon that is still on screen after the session changed cannot open a
 * blocked app.
 */
class CheckAppLaunch(
    private val sessions: ObserveFocusSession,
    private val essentialApps: EssentialAppsSource,
) {
    suspend operator fun invoke(packageName: String): Boolean =
        AppAccess(sessions.current(), essentialApps.essentialApps.first()).canLaunch(packageName)
}
