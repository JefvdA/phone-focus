package dev.phonefocus.application

import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.AppRuleOptions
import dev.phonefocus.domain.EssentialApps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * An installed app with a launcher entry, as shown in the app drawer.
 *
 * [packageName] doubles as the icon reference: `ui`/`infrastructure` load the icon for it
 * (`AppIconLoader`), so no Android graphics types reach this layer.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
)

/** One row of the app catalogue: the [app] and the [ruleOptions] the setup offers for it. */
data class AppCatalogueEntry(
    val app: InstalledApp,
    val ruleOptions: List<AppRule>,
    /** True for this app, Settings and the dialer, which are always allowed ([EssentialApps]). */
    val essential: Boolean,
)

/**
 * The catalogue of installed, launchable apps for the focus setup (#7) and the launcher (#8),
 * sorted by label. Updates when apps are installed, removed or updated, or the default dialer
 * changes.
 *
 * Read-only: it never touches the focus session, so an app installed during focus is not added
 * to the policy and stays [AppRule.Blocked] (unlisted packages are Blocked) until the user
 * explicitly allows it once unlocked.
 */
class ObserveAppCatalogue(
    private val installedApps: InstalledAppsSource,
    private val essentialApps: EssentialAppsSource,
) {
    operator fun invoke(): Flow<List<AppCatalogueEntry>> =
        combine(installedApps.apps, essentialApps.essentialApps) { apps, essential ->
            apps.distinctBy { it.packageName }
                .sortedWith(BY_LABEL)
                .map { app ->
                    AppCatalogueEntry(
                        app = app,
                        ruleOptions = AppRuleOptions.forPackage(app.packageName, essential),
                        essential = app.packageName in essential,
                    )
                }
        }.distinctUntilChanged()

    private companion object {
        val BY_LABEL: Comparator<InstalledApp> =
            compareBy(String.CASE_INSENSITIVE_ORDER) { app: InstalledApp -> app.label }
                .thenBy { it.packageName }
    }
}
