package dev.phonefocus.infrastructure

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.preferencesDataStore
import dev.phonefocus.application.ExitDelayOptions
import dev.phonefocus.application.FocusCommands
import dev.phonefocus.application.ObserveFocusSession
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.infrastructure.persistence.DataStoreFocusSessionRepository
import dev.phonefocus.infrastructure.persistence.FocusRecordCodec
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

    val commands = FocusCommands(
        repository = repository,
        clock = AndroidDeviceClock(appContext.contentResolver),
        scheduler = AlarmUnlockScheduler(appContext),
    )

    val observeSession = ObserveFocusSession(repository)

    val isDebugBuild: Boolean = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val exitDelays: List<ExitDelay> = ExitDelayOptions.available(isDebugBuild)
}
