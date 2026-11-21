package dev.phonefocus.ui.launcher

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.phonefocus.application.CheckAppLaunch
import dev.phonefocus.application.ExitCountdown
import dev.phonefocus.application.FocusCommands
import dev.phonefocus.application.FocusDesktop
import dev.phonefocus.application.ObserveFocusDesktop
import dev.phonefocus.domain.FocusState
import dev.phonefocus.infrastructure.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration

/** Which window is open on the desktop; the Focus dialog is hidden by default. */
enum class LauncherDialog { Menu, Focus }

/** What the focus launcher shows. */
data class LauncherUiState(
    /** Session and desktop icons; null while loading. */
    val desktop: FocusDesktop? = null,
    /** Time left on the exit countdown (trusted time); null when none runs. */
    val remaining: Duration? = null,
    val dialog: LauncherDialog? = null,
)

/**
 * State holder of the focus launcher (#8). Apps come from [ObserveFocusDesktop]; a tap is
 * checked with [CheckAppLaunch] before [launchApp] starts the app. While an exit countdown
 * runs, the remaining time is re-read every second from [ExitCountdown].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModel(
    observeDesktop: ObserveFocusDesktop,
    private val checkAppLaunch: CheckAppLaunch,
    private val countdown: ExitCountdown,
    private val commands: FocusCommands,
    private val launchApp: (packageName: String) -> Boolean,
) : ViewModel() {
    private val dialog = MutableStateFlow<LauncherDialog?>(null)

    private val desktops: Flow<FocusDesktop?> = observeDesktop()

    private val remaining: Flow<Duration?> = desktops
        .map { it?.session?.state }
        .distinctUntilChanged()
        .flatMapLatest { state ->
            if (state is FocusState.ExitPending) countdownTicks() else flowOf(null)
        }

    val state: StateFlow<LauncherUiState> =
        combine(desktops, remaining, dialog) { desktop, remaining, dialog ->
            LauncherUiState(desktop, remaining, dialog)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LauncherUiState())

    /** The remaining time now and then every second; nudges the unlock once it reaches zero. */
    private fun countdownTicks(): Flow<Duration?> = flow {
        while (true) {
            emit(countdown.remaining())
            delay(TICK_MS)
        }
    }.distinctUntilChanged().onEach { left ->
        // The unlock alarm normally ends the countdown; this covers a late or missed alarm.
        if (left == Duration.ZERO) tick()
    }

    fun openDialog(which: LauncherDialog) {
        dialog.value = which
    }

    fun closeDialog() {
        dialog.value = null
    }

    /** Opens [packageName] if [CheckAppLaunch] allows it right now. */
    fun open(packageName: String) {
        viewModelScope.launch {
            if (!checkAppLaunch(packageName)) {
                Log.i(TAG, "not launching $packageName: not allowed now")
                return@launch
            }
            if (!launchApp(packageName)) Log.w(TAG, "could not launch $packageName")
        }
    }

    /** Advances time (e.g. when the desktop comes back on screen) so an ended countdown unlocks. */
    fun tick() {
        viewModelScope.launch {
            runCatching { commands.tick() }.onFailure { Log.e(TAG, "tick failed", it) }
        }
    }

    companion object {
        private const val TAG = "LauncherViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val TICK_MS = 1_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LauncherViewModel(
                    observeDesktop = container.observeFocusDesktop,
                    checkAppLaunch = container.checkAppLaunch,
                    countdown = container.exitCountdown,
                    commands = container.commands,
                    launchApp = container.appLauncher::launch,
                )
            }
        }
    }
}
