package dev.phonefocus.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.phonefocus.application.AppCatalogueEntry
import dev.phonefocus.application.FocusSetupForm
import dev.phonefocus.application.InstalledApp
import dev.phonefocus.application.PrerequisiteStatus
import dev.phonefocus.application.SetupAppChoice
import dev.phonefocus.application.StartPrerequisite
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusState
import dev.phonefocus.ui.components.RetroButton
import dev.phonefocus.ui.components.RetroDropdown
import dev.phonefocus.ui.components.RetroRadio
import dev.phonefocus.ui.components.RetroSunkenPanel
import dev.phonefocus.ui.components.RetroWindow
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme
import dev.phonefocus.ui.theme.RetroTypography
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the user can do on the setup screen. */
class FocusSetupActions(
    val onRuleChange: (packageName: String, rule: AppRule) -> Unit,
    val onExitDelayChange: (ExitDelay) -> Unit,
    val onStart: () -> Unit,
    val onConfirmStart: () -> Unit,
    val onDismissConfirmation: () -> Unit,
    val onOpenSettings: (StartPrerequisite) -> Unit,
    /** Cancel / close: back to the normal phone. */
    val onCancel: () -> Unit,
)

/**
 * Focus setup (mockup C2, #7): a rule per installed app, the exit delay, and "Start focus mode"
 * with a confirmation. Only usable while Unlocked; otherwise it says that focus is on (the
 * domain rejects loosening anyway). [debugContent] (debug builds only) is reachable through a
 * "Debug" button.
 */
@Composable
fun FocusSetupScreen(
    state: FocusSetupUiState,
    actions: FocusSetupActions,
    appIcon: @Composable (packageName: String) -> Unit,
    debugContent: (@Composable () -> Unit)? = null,
) {
    var showDebug by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(RetroColors.Desktop).safeDrawingPadding().padding(8.dp)) {
        RetroWindow(title = "Focus Setup", modifier = Modifier.fillMaxSize(), onClose = actions.onCancel) {
            val focusState = state.focusState
            val form = state.form
            when {
                focusState == null || (focusState == FocusState.Unlocked && form == null) ->
                    Text("Loading…", style = RetroTypography.Body)
                focusState.isFocusActive || form == null -> FocusActiveMessage(focusState, actions.onCancel)
                else -> SetupForm(state, form, actions, appIcon)
            }
            if (debugContent != null) {
                Spacer(Modifier.height(8.dp))
                RetroButton("Debug…", onClick = { showDebug = true })
            }
        }
    }
    if (state.confirming && state.form != null) {
        ConfirmStartDialog(state.form.exitDelay, actions.onConfirmStart, actions.onDismissConfirmation)
    }
    if (showDebug && debugContent != null) {
        Dialog(onDismissRequest = { showDebug = false }) {
            RetroWindow(title = "Debug", onClose = { showDebug = false }) {
                Column(Modifier.verticalScroll(rememberScrollState())) { debugContent() }
            }
        }
    }
}

@Composable
private fun ColumnScope.SetupForm(
    state: FocusSetupUiState,
    form: FocusSetupForm,
    actions: FocusSetupActions,
    appIcon: @Composable (String) -> Unit,
) {
    GroupLabel("Apps (all installed)")
    RetroSunkenPanel(Modifier.weight(1f).fillMaxWidth(), background = RetroColors.Surface) {
        LazyColumn(Modifier.padding(RetroDimens.Bevel + 4.dp)) {
            items(form.apps, key = { it.packageName }) { choice ->
                AppRuleRow(choice, onRuleChange = { actions.onRuleChange(choice.packageName, it) }, appIcon = appIcon)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    GroupLabel("Exit delay")
    Column(Modifier.selectableGroup()) {
        form.exitDelayOptions.chunked(2).forEach { row ->
            Row {
                row.forEach { delay ->
                    RetroRadio(
                        selected = delay == form.exitDelay,
                        onClick = { actions.onExitDelayChange(delay) },
                        label = SetupLabels.exitDelay(delay),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    val missing = state.missingPrerequisites
    if (missing.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        PrerequisitesPanel(missing, actions.onOpenSettings)
    }
    state.error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = RetroTypography.Label)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RetroButton("Cancel", onClick = actions.onCancel, modifier = Modifier.weight(1f))
        RetroButton(
            "Start focus mode",
            onClick = actions.onStart,
            enabled = state.canStart,
            isDefault = true,
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = RetroTypography.Title, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun AppRuleRow(choice: SetupAppChoice, onRuleChange: (AppRule) -> Unit, appIcon: @Composable (String) -> Unit) {
    val options = choice.entry.ruleOptions
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = RetroDimens.MinTouchTarget).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        appIcon(choice.packageName)
        Text(
            choice.entry.app.label,
            style = RetroTypography.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RetroDropdown(
            options = options,
            selected = choice.rule,
            onSelect = onRuleChange,
            // Essential apps (this app, Settings, the dialer) are always allowed.
            enabled = options.size > 1,
            optionLabel = SetupLabels::rule,
            isHighlighted = { it == AppRule.ShortVideoBlocked },
            modifier = Modifier.width(184.dp),
        )
    }
}

@Composable
private fun PrerequisitesPanel(missing: List<StartPrerequisite>, onOpenSettings: (StartPrerequisite) -> Unit) {
    RetroSunkenPanel(Modifier.fillMaxWidth(), background = RetroColors.Highlight) {
        Column(Modifier.padding(RetroDimens.Bevel + 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Before you start:", style = RetroTypography.Body)
            missing.forEach { prerequisite ->
                Text(SetupLabels.prerequisite(prerequisite), style = RetroTypography.Label)
                RetroButton("Open settings", onClick = { onOpenSettings(prerequisite) })
            }
        }
    }
}

@Composable
private fun FocusActiveMessage(state: FocusState, onClose: () -> Unit) {
    val text = when (state) {
        is FocusState.ExitPending -> {
            val at = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
            "Focus mode is on. Exit requested: the phone unlocks at ${at.format(state.unlockAt)}."
        }
        else -> "Focus mode is on. Your settings are locked in and can only be made stricter."
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = RetroTypography.Body)
        RetroButton("Close", onClick = onClose, isDefault = true)
    }
}

@Composable
private fun ConfirmStartDialog(delay: ExitDelay, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        RetroWindow(title = "Start focus mode?", onClose = onDismiss) {
            Text(SetupLabels.confirmation(delay), style = RetroTypography.Body)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RetroButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                RetroButton("Start", onClick = onConfirm, isDefault = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

private val previewApps = listOf(
    Triple("com.example.phone", "Phone", listOf(AppRule.Allowed)) to AppRule.Allowed,
    Triple("com.example.maps", "Maps", listOf(AppRule.Allowed, AppRule.Blocked)) to AppRule.Allowed,
    Triple("com.instagram.android", "Instagram", AppRule.entries) to AppRule.ShortVideoBlocked,
    Triple("com.example.browser", "Browser", listOf(AppRule.Allowed, AppRule.Blocked)) to AppRule.Blocked,
).map { (app, rule) ->
    SetupAppChoice(AppCatalogueEntry(InstalledApp(app.first, app.second), app.third, essential = app.third.size == 1), rule)
}

private val previewActions = FocusSetupActions({ _, _ -> }, {}, {}, {}, {}, {}, {})

@Preview(widthDp = 380, heightDp = 800)
@Composable
private fun FocusSetupScreenPreview() {
    RetroTheme {
        FocusSetupScreen(
            state = FocusSetupUiState(
                focusState = FocusState.Unlocked,
                form = FocusSetupForm(previewApps, ExitDelay.ONE_DAY, ExitDelay.PRESETS),
                prerequisites = mapOf(StartPrerequisite.DefaultHome to PrerequisiteStatus.Missing),
            ),
            actions = previewActions,
            appIcon = { Spacer(Modifier.size(24.dp)) },
        )
    }
}
