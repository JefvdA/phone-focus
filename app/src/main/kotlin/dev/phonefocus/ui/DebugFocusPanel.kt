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
import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.FocusState
import dev.phonefocus.infrastructure.AppContainer
import kotlinx.coroutines.launch

/**
 * Debug builds only: shows the persisted focus state and drives it with the one-minute debug
 * delay, for the manual reboot test in #4. Replaced by the real screens (#7, #8, #10).
 */
@Composable
fun DebugFocusPanel(container: AppContainer) {
    val sessions = remember(container) { container.observeSession() }
    val session by sessions.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val commands = container.commands

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
        Button(onClick = {
            scope.launch { commands.startFocus(AppPolicy.EMPTY, ExitDelayOptions.DEBUG_ONE_MINUTE) }
        }) { Text("Start focus (1 min delay)") }
        Button(onClick = { scope.launch { commands.requestExit() } }) { Text("Request exit") }
        Button(onClick = { scope.launch { commands.cancelExit() } }) { Text("Cancel exit") }
    }
}
