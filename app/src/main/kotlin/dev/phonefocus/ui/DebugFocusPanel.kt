package dev.phonefocus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.phonefocus.application.ExitDelayOptions
import dev.phonefocus.application.PrerequisiteStatus
import dev.phonefocus.application.StartPrerequisite
import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.FocusState
import dev.phonefocus.infrastructure.AppContainer
import kotlinx.coroutines.launch

/**
 * Debug builds only, behind the "Debug…" button of the setup screen: shows the persisted focus
 * state and the start prerequisites, and drives the session with the one-minute debug delay
 * (manual reboot test in #4). "Request exit" / "Cancel exit" stand in for the launcher's
 * escape flow until #8 / #10 exist.
 */
@Composable
fun DebugFocusPanel(
    container: AppContainer,
    versionLabel: String,
    prerequisites: Map<StartPrerequisite, PrerequisiteStatus>,
) {
    val sessions = remember(container) { container.observeSession() }
    val session by sessions.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val commands = container.commands

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(versionLabel, style = MaterialTheme.typography.bodySmall)
        val state = session?.state
        Text(
            text = when (state) {
                null -> "Loading…"
                FocusState.Unlocked -> "Debug: Unlocked"
                FocusState.Focused -> "Debug: Focused"
                is FocusState.ExitPending -> "Debug: Exit pending, unlocks at ${state.unlockAt}"
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        prerequisites.forEach { (prerequisite, status) ->
            Text("$prerequisite: $status", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = {
            scope.launch { commands.startFocus(AppPolicy.EMPTY, ExitDelayOptions.DEBUG_ONE_MINUTE) }
        }) { Text("Start focus (empty policy, 1 min delay)") }
        Button(onClick = { scope.launch { commands.requestExit() } }) { Text("Request exit") }
        Button(onClick = { scope.launch { commands.cancelExit() } }) { Text("Cancel exit") }
    }
}
