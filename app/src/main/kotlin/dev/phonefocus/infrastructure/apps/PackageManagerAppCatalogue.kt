package dev.phonefocus.infrastructure.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.telecom.TelecomManager
import dev.phonefocus.application.EssentialAppsSource
import dev.phonefocus.application.InstalledApp
import dev.phonefocus.application.InstalledAppsSource
import dev.phonefocus.domain.EssentialApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * Installed launchable apps and essential apps from the [PackageManager].
 *
 * Apps are found with an `ACTION_MAIN` + `CATEGORY_LAUNCHER` query, made visible by the
 * matching `<queries>` entry in the manifest (no `QUERY_ALL_PACKAGES`). The result is
 * re-queried on package broadcasts (added, removed, replaced, changed i.e. enabled/disabled),
 * on locale changes (labels) and when the default dialer changes.
 */
class PackageManagerAppCatalogue(
    context: Context,
    scope: CoroutineScope,
) : InstalledAppsSource, EssentialAppsSource {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    /** Emits once on collection and again on every relevant system broadcast. */
    private val changes: Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val packageFilter = IntentFilter().apply {
            PACKAGE_ACTIONS.forEach(::addAction)
            addDataScheme("package")
        }
        val systemFilter = IntentFilter().apply {
            addAction(TelecomManager.ACTION_DEFAULT_DIALER_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        appContext.registerReceiver(receiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)
        // Second registration of the same receiver: the package filter needs a data scheme, these don't.
        appContext.registerReceiver(receiver, systemFilter, Context.RECEIVER_NOT_EXPORTED)
        trySend(Unit)
        awaitClose { appContext.unregisterReceiver(receiver) }
    }.conflate()

    override val apps: Flow<List<InstalledApp>> = changes
        .map { queryLaunchableApps() }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    override val essentialApps: Flow<EssentialApps> = changes
        .map { resolveEssentialApps() }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** One entry per package with a launcher activity, as in the app drawer. */
    private fun queryLaunchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        return activities
            .groupBy { it.activityInfo.packageName }
            .map { (packageName, entries) ->
                // A single launcher entry shows its own label in the drawer; for several, use the app's.
                val label = entries.singleOrNull()?.loadLabel(packageManager)
                    ?: entries.first().activityInfo.applicationInfo.loadLabel(packageManager)
                InstalledApp(packageName, label.toString().ifBlank { packageName })
            }
    }

    /** This app, the Settings app and the default dialer, resolved on this device. */
    private fun resolveEssentialApps(): EssentialApps {
        val settings = resolvePackage(Intent(Settings.ACTION_SETTINGS)) ?: FALLBACK_SETTINGS
        val telecom = appContext.getSystemService(TelecomManager::class.java)
        val dialer = telecom?.defaultDialerPackage
            ?: resolvePackage(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))
            ?: telecom?.systemDialerPackage
        return EssentialApps(setOfNotNull(appContext.packageName, settings, dialer))
    }

    private fun resolvePackage(intent: Intent): String? =
        packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            ?.activityInfo?.packageName
            // The resolver ("choose an app") dialog means there is no single default.
            ?.takeIf { it != "android" }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val FALLBACK_SETTINGS = "com.android.settings"

        val PACKAGE_ACTIONS = listOf(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_CHANGED,
        )
    }
}
