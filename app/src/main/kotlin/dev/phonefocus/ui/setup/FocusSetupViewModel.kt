package dev.phonefocus.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.phonefocus.application.AppCatalogueEntry
import dev.phonefocus.application.FocusSetupForm
import dev.phonefocus.application.ObserveAppCatalogue
import dev.phonefocus.application.ObserveFocusSession
import dev.phonefocus.application.PrerequisiteStatus
import dev.phonefocus.application.SetupStartResult
import dev.phonefocus.application.StartFocusFromSetup
import dev.phonefocus.application.StartPrerequisite
import dev.phonefocus.application.StartPrerequisites
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.FocusState
import dev.phonefocus.infrastructure.AppContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the focus setup screen shows. */
data class FocusSetupUiState(
    /** The session's state; null while loading. The form is only usable while Unlocked. */
    val focusState: FocusState? = null,
    /** The choices; null while the app catalogue loads. */
    val form: FocusSetupForm? = null,
    val prerequisites: Map<StartPrerequisite, PrerequisiteStatus> = emptyMap(),
    /** The confirmation dialog is open. */
    val confirming: Boolean = false,
    val starting: Boolean = false,
    /** A one-line problem to show, e.g. a rejected start. */
    val error: String? = null,
) {
    val missingPrerequisites: List<StartPrerequisite>
        get() = prerequisites.filterValues { it == PrerequisiteStatus.Missing }.keys.toList()

    val canStart: Boolean
        get() = focusState == FocusState.Unlocked && form != null && !starting && missingPrerequisites.isEmpty()
}

/** Things that happen once, for the activity to act on. */
sealed interface FocusSetupEvent {
    /** Focus has started: switch to the focus launcher. */
    data object FocusStarted : FocusSetupEvent
}

/**
 * State holder of the focus setup screen (#7). Holds only the user's edits and dialog state;
 * defaults and the policy come from [FocusSetupForm], starting from [StartFocusFromSetup].
 */
class FocusSetupViewModel(
    observeSession: ObserveFocusSession,
    observeCatalogue: ObserveAppCatalogue,
    private val exitDelays: List<ExitDelay>,
    private val prerequisites: StartPrerequisites,
    private val startFocus: StartFocusFromSetup,
) : ViewModel() {
    private data class Edits(val rules: Map<String, AppRule> = emptyMap(), val delay: ExitDelay? = null)

    private data class Screen(
        val prerequisites: Map<StartPrerequisite, PrerequisiteStatus> = emptyMap(),
        val confirming: Boolean = false,
        val starting: Boolean = false,
        val error: String? = null,
    )

    private val edits = MutableStateFlow(Edits())
    private val screen = MutableStateFlow(Screen())
    private val events = Channel<FocusSetupEvent>(Channel.BUFFERED)

    private val sessions: Flow<FocusSession?> = observeSession()
    private val catalogue: Flow<List<AppCatalogueEntry>?> = observeCatalogue()

    val state: StateFlow<FocusSetupUiState> =
        combine(sessions, catalogue, edits, screen) { session, apps, edits, screen ->
            FocusSetupUiState(
                focusState = session?.state,
                form = if (session != null && apps != null) {
                    FocusSetupForm.create(apps, session, exitDelays, edits.rules, edits.delay)
                } else {
                    null
                },
                prerequisites = screen.prerequisites,
                confirming = screen.confirming,
                starting = screen.starting,
                error = screen.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FocusSetupUiState())

    val oneTimeEvents: Flow<FocusSetupEvent> = events.receiveAsFlow()

    fun setRule(packageName: String, rule: AppRule) {
        edits.update { it.copy(rules = it.rules + (packageName to rule)) }
    }

    fun setExitDelay(delay: ExitDelay) {
        edits.update { it.copy(delay = delay) }
    }

    /** Re-checks the prerequisites, e.g. when the user comes back from the system settings. */
    fun refreshPrerequisites() {
        viewModelScope.launch {
            val statuses = prerequisites.statuses()
            screen.update { it.copy(prerequisites = statuses) }
        }
    }

    /** "Start focus mode": opens the confirmation dialog once nothing is missing. */
    fun requestStart() {
        viewModelScope.launch {
            val statuses = prerequisites.statuses()
            val missing = statuses.any { it.value == PrerequisiteStatus.Missing }
            screen.update { it.copy(prerequisites = statuses, confirming = !missing, error = null) }
        }
    }

    fun dismissConfirmation() {
        screen.update { it.copy(confirming = false) }
    }

    /** Confirmed in the dialog: starts focus with the current choices. */
    fun confirmStart() {
        val form = state.value.form ?: return
        if (screen.value.starting) return
        screen.update { it.copy(confirming = false, starting = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { startFocus(form) }.getOrElse {
                screen.update { s -> s.copy(starting = false, error = "Could not start focus mode.") }
                return@launch
            }
            when (result) {
                SetupStartResult.Started -> {
                    edits.value = Edits()
                    screen.update { it.copy(starting = false) }
                    events.send(FocusSetupEvent.FocusStarted)
                }
                is SetupStartResult.PrerequisitesMissing -> {
                    refreshPrerequisites()
                    screen.update { it.copy(starting = false) }
                }
                is SetupStartResult.Rejected ->
                    screen.update { it.copy(starting = false, error = "Focus mode could not be started (${result.error}).") }
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FocusSetupViewModel(
                    observeSession = container.observeSession,
                    observeCatalogue = container.observeAppCatalogue,
                    exitDelays = container.exitDelays,
                    prerequisites = container.startPrerequisites,
                    startFocus = container.startFocusFromSetup,
                )
            }
        }
    }
}
