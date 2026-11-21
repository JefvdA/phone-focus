package dev.phonefocus.infrastructure

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.preferencesDataStore
import dev.phonefocus.application.AppBlockerStatusSource
import dev.phonefocus.application.CheckAppLaunch
import dev.phonefocus.application.ExitCountdown
import dev.phonefocus.application.ExitDelayOptions
import dev.phonefocus.application.FocusCommands
import dev.phonefocus.application.ObserveAppBlockerWarning
import dev.phonefocus.application.ObserveAppBlocking
import dev.phonefocus.application.ObserveAppCatalogue
import dev.phonefocus.application.ObserveFocusDesktop
import dev.phonefocus.application.ObserveFocusSession
import dev.phonefocus.application.StartFocusFromSetup
import dev.phonefocus.application.StartPrerequisites
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.infrastructure.apps.AppIconLoader
import dev.phonefocus.infrastructure.apps.AppLauncher
import dev.phonefocus.infrastructure.apps.PackageManagerAppCatalogue
import dev.phonefocus.infrastructure.persistence.DataStoreFocusSessionRepository
import dev.phonefocus.infrastructure.persistence.FocusRecordCodec
import dev.phonefocus.infrastructure.system.AndroidAppBlockerStatus
import dev.phonefocus.infrastructure.system.AndroidFocusNotifier
import dev.phonefocus.infrastructure.system.AndroidStartPrerequisites
import dev.phonefocus.infrastructure.system.AndroidSystemPackages
import dev.phonefocus.infrastructure.time.AlarmUnlockScheduler
import dev.phonefocus.infrastructure.time.AndroidDeviceClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// A corrupt file is replaced by a Focused session, never by an empty (= Unlocked) one.
private val Context.focusDataStore by preferencesDataStore(
    name = "focus_session",
    corruptionHandler = ReplaceFileCorruptionHandler { FocusRecordCodec.corruptionFallback() },
)

/** Manual dependency wiring; one instance per process, owned by [PhoneFocusApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Process-wide scope for work that must outlive a screen (receivers, app start). */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val repository = DataStoreFocusSessionRepository(appContext.focusDataStore)

    private val deviceClock = AndroidDeviceClock(appContext.contentResolver)

    val commands = FocusCommands(
        repository = repository,
        clock = deviceClock,
        scheduler = AlarmUnlockScheduler(appContext),
        notifier = AndroidFocusNotifier(appContext),
    )

    val observeSession = ObserveFocusSession(repository)

    private val appCatalogue = PackageManagerAppCatalogue(appContext, scope)

    val observeAppCatalogue = ObserveAppCatalogue(appCatalogue, appCatalogue)

    val appIcons = AppIconLoader(appContext)

    val observeFocusDesktop = ObserveFocusDesktop(observeSession, appCatalogue, appCatalogue, appContext.packageName)

    val checkAppLaunch = CheckAppLaunch(observeSession, appCatalogue)

    val appLauncher = AppLauncher(appContext)

    val exitCountdown = ExitCountdown(repository, deviceClock)

    val isDebugBuild: Boolean = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val exitDelays: List<ExitDelay> = ExitDelayOptions.available(isDebugBuild)

    val startPrerequisites: StartPrerequisites = AndroidStartPrerequisites(appContext)

    val startFocusFromSetup = StartFocusFromSetup(commands, startPrerequisites)

    /** What the app blocker (accessibility service, #11) judges the app in front with. */
    val observeAppBlocking = ObserveAppBlocking(
        observeSession,
        appCatalogue,
        AndroidSystemPackages(appContext, scope),
        appCatalogue,
    )

    val appBlockerStatus: AppBlockerStatusSource = AndroidAppBlockerStatus(appContext, startPrerequisites)

    val observeAppBlockerWarning = ObserveAppBlockerWarning(observeSession, appBlockerStatus)
}
