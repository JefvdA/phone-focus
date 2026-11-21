package dev.phonefocus.ui.launcher

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.phonefocus.application.AppBlockerStatusSource
import dev.phonefocus.application.AppCatalogueEntry
import dev.phonefocus.application.CheckAppLaunch
import dev.phonefocus.application.ExitCountdown
import dev.phonefocus.application.ExitCountdownStatus
import dev.phonefocus.application.FocusCommands
import dev.phonefocus.application.FocusDesktop
import dev.phonefocus.application.MakeStricterForm
import dev.phonefocus.application.ObserveAppBlockerWarning
import dev.phonefocus.application.ObserveAppCatalogue
import dev.phonefocus.application.ObserveFocusDesktop
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusState
import dev.phonefocus.infrastructure.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Which window is open on the desktop; the Focus dialog is hidden by default. [Focus] shows the
 * exit-pending dialog (C5) instead while an exit countdown runs. [EmergencyEscape] is the
 * confirmation before requesting exit.
 */
enum class LauncherDialog { Menu, Focus, MakeStricter, EmergencyEscape }

/** The emergency escape / exit-pending flow (C5, #10). */
data class ExitUiState(
    /** A request or cancel is running. */
    val busy: Boolean = false,
    /** A one-line problem to show, e.g. a rejected request. */
    val error: String? = null,
)

/** The "Make stricter" dialog (C4, #9). */
data class MakeStricterUiState(
    /** The choices; null while loading or when focus is off. */
    val form: MakeStricterForm? = null,
    /** The confirmation step before applying is open. */
    val confirming: Boolean = false,
    val applying: Boolean = false,
    /** A one-line problem to show, e.g. changes the domain rejected. */
    val error: String? = null,
) {
    val canApply: Boolean get() = form?.hasChanges == true && !applying
}

/** What the focus launcher shows. */
data class LauncherUiState(
    /** Session and desktop icons; null while loading. */
    val desktop: FocusDesktop? = null,
    /** The exit countdown (trusted time): time left and progress; null when none runs. */
    val countdown: ExitCountdownStatus? = null,
    val dialog: LauncherDialog? = null,
    val stricter: MakeStricterUiState = MakeStricterUiState(),
    val exit: ExitUiState = ExitUiState(),
    /** Focus is on but the app blocker (accessibility service, #11) is off: warn. */
    val blockerOff: Boolean = false,
    /** A blocked app was just sent back here: show "Blocked during focus" briefly. */
    val blockedNotice: Boolean = false,
) {
    /** Time left on the exit countdown; null when none runs. */
    val remaining: Duration? get() = countdown?.remaining
}

/**
 * State holder of the focus launcher (#8). Apps come from [ObserveFocusDesktop]; a tap is
 * checked with [CheckAppLaunch] before [launchApp] starts the app. While an exit countdown
 * runs, the remaining time is re-read every second from [ExitCountdown]. When focus ends, the
 * focus windows close, so the "Focus is off" window with "Switch home back…" is in front.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModel(
    observeDesktop: ObserveFocusDesktop,
    private val checkAppLaunch: CheckAppLaunch,
    private val countdown: ExitCountdown,
    private val commands: FocusCommands,
    private val launchApp: (packageName: String) -> Boolean,
    observeCatalogue: ObserveAppCatalogue,
    private val exitDelays: List<ExitDelay>,
    observeBlockerWarning: ObserveAppBlockerWarning,
    private val blockerStatus: AppBlockerStatusSource,
) : ViewModel() {
    private val dialog = MutableStateFlow<LauncherDialog?>(null)

    /** The user's edits and the dialog state of "Make stricter"; the form is rebuilt from them. */
    private data class StricterEdits(
        val rules: Map<String, AppRule> = emptyMap(),
        val delay: ExitDelay? = null,
        val confirming: Boolean = false,
        val applying: Boolean = false,
        val error: String? = null,
    )

    private val stricterEdits = MutableStateFlow(StricterEdits())

    private val catalogue: Flow<List<AppCatalogueEntry>?> = observeCatalogue()

    private val exitUi = MutableStateFlow(ExitUiState())

    private val desktops: Flow<FocusDesktop?> = observeDesktop()

    private val countdownStatus: Flow<ExitCountdownStatus?> = desktops
        .map { it?.session?.state }
        .distinctUntilChanged()
        .onEach { if (it == FocusState.Unlocked) closeFocusWindows() }
        .flatMapLatest { state ->
            if (state is FocusState.ExitPending) countdownTicks() else flowOf(null)
        }

    private val blockedNotice = MutableStateFlow(false)
    private var blockedNoticeJob: Job? = null

    /** Dialog, exit flow and the app blocker's warning and notice. */
    private data class Windows(
        val dialog: LauncherDialog?,
        val exit: ExitUiState,
        val blockerOff: Boolean,
        val blockedNotice: Boolean,
    )

    private val windows: Flow<Windows> =
        combine(dialog, exitUi, observeBlockerWarning(), blockedNotice, ::Windows)

    val state: StateFlow<LauncherUiState> =
        combine(desktops, countdownStatus, windows, catalogue, stricterEdits) { desktop, countdown, windows, apps, edits ->
            val session = desktop?.session
            val form = if (session != null && session.state.isFocusActive && apps != null) {
                MakeStricterForm.create(apps, session, exitDelays, edits.rules, edits.delay)
            } else {
                null
            }
            val stricter = MakeStricterUiState(form, edits.confirming && form?.hasChanges == true, edits.applying, edits.error)
            LauncherUiState(
                desktop,
                countdown,
                windows.dialog,
                stricter,
                windows.exit,
                blockerOff = windows.blockerOff,
                blockedNotice = windows.blockedNotice,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LauncherUiState())

    /** The countdown now and then every second; nudges the unlock once it reaches zero. */
    private fun countdownTicks(): Flow<ExitCountdownStatus?> = flow {
        while (true) {
            emit(countdown.status())
            delay(TICK_MS)
        }
    }.distinctUntilChanged().onEach { status ->
        // The unlock alarm normally ends the countdown; this covers a late or missed alarm.
        if (status?.remaining == Duration.ZERO) tick()
    }

    fun openDialog(which: LauncherDialog) {
        // "Make stricter" always starts from the session as it is now.
        if (which == LauncherDialog.MakeStricter && dialog.value != which) stricterEdits.value = StricterEdits()
        if (which == LauncherDialog.EmergencyEscape) exitUi.value = ExitUiState()
        dialog.value = which
    }

    /**
     * Confirmed "Emergency escape": starts the exit countdown with `requestExit`. On success the
     * Focus window shows the exit-pending dialog with the countdown.
     */
    fun confirmEmergencyEscape() = runExitCommand(LauncherLabels.EXIT_REQUEST_FAILED) { commands.requestExit() }

    /** "Cancel exit, stay focused": stops the countdown with `cancelExit`; focus stays on. */
    fun cancelExit() = runExitCommand(LauncherLabels.EXIT_CANCEL_FAILED) { commands.cancelExit() }

    private fun runExitCommand(failed: String, command: suspend () -> FocusResult) {
        if (exitUi.value.busy) return
        exitUi.value = ExitUiState(busy = true)
        viewModelScope.launch {
            val result = runCatching { command() }.getOrElse {
                Log.e(TAG, "exit command failed", it)
                exitUi.value = ExitUiState(error = failed)
                return@launch
            }
            when (result) {
                is FocusResult.Success -> {
                    exitUi.value = ExitUiState()
                    if (dialog.value == LauncherDialog.EmergencyEscape) dialog.value = LauncherDialog.Focus
                }
                is FocusResult.Failure -> exitUi.value = ExitUiState(error = LauncherLabels.exitRejected(result.error))
            }
        }
    }

    /** Focus has ended: the focus windows are gone, so "Focus is off" is in front. */
    private fun closeFocusWindows() {
        dialog.update { if (it == LauncherDialog.Menu) it else null }
        exitUi.value = ExitUiState()
    }

    fun setStricterRule(packageName: String, rule: AppRule) {
        stricterEdits.update { it.copy(rules = it.rules + (packageName to rule), error = null) }
    }

    fun setStricterDelay(delay: ExitDelay) {
        stricterEdits.update { it.copy(delay = delay, error = null) }
    }

    /** "Apply…": asks for confirmation first, since the changes can't be undone while focused. */
    fun requestApplyStricter() {
        if (state.value.stricter.canApply) stricterEdits.update { it.copy(confirming = true, error = null) }
    }

    fun dismissStricterConfirmation() {
        stricterEdits.update { it.copy(confirming = false) }
    }

    /**
     * Confirmed: applies the changes with `tighten`. The domain is the final guard; if it rejects
     * them nothing changes and the reason is shown. On success the Focus dialog comes back.
     */
    fun confirmApplyStricter() {
        val form = state.value.stricter.form ?: return
        if (stricterEdits.value.applying || !form.hasChanges) return
        stricterEdits.update { it.copy(confirming = false, applying = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { commands.tighten(form.tightening()) }.getOrElse {
                Log.e(TAG, "tighten failed", it)
                stricterEdits.update { e -> e.copy(applying = false, error = LauncherLabels.STRICTER_FAILED) }
                return@launch
            }
            when (result) {
                is FocusResult.Success -> {
                    stricterEdits.value = StricterEdits()
                    if (dialog.value == LauncherDialog.MakeStricter) dialog.value = LauncherDialog.Focus
                }
                is FocusResult.Failure -> stricterEdits.update {
                    it.copy(applying = false, error = LauncherLabels.tightenRejected(result.error))
                }
            }
        }
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

    /** A blocked app was sent back to the desktop: show "Blocked during focus" for a moment. */
    fun showBlockedNotice() {
        blockedNoticeJob?.cancel()
        blockedNotice.value = true
        blockedNoticeJob = viewModelScope.launch {
            delay(BLOCKED_NOTICE_MS)
            blockedNotice.value = false
        }
    }

    /** Checks again whether the app blocker is on, e.g. when the desktop comes back on screen. */
    fun refreshBlockerStatus() {
        blockerStatus.refresh()
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
        private const val BLOCKED_NOTICE_MS = 2_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LauncherViewModel(
                    observeDesktop = container.observeFocusDesktop,
                    checkAppLaunch = container.checkAppLaunch,
                    countdown = container.exitCountdown,
                    commands = container.commands,
                    launchApp = container.appLauncher::launch,
                    observeCatalogue = container.observeAppCatalogue,
                    exitDelays = container.exitDelays,
                    observeBlockerWarning = container.observeAppBlockerWarning,
                    blockerStatus = container.appBlockerStatus,
                )
            }
        }
    }
}
