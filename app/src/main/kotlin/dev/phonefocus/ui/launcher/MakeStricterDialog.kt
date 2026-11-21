package dev.phonefocus.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.phonefocus.application.AppCatalogueEntry
import dev.phonefocus.application.ExitDelayOptions
import dev.phonefocus.application.InstalledApp
import dev.phonefocus.application.MakeStricterForm
import dev.phonefocus.application.StricterAppChoice
import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.AppRuleOptions
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.ui.components.RetroButton
import dev.phonefocus.ui.components.RetroDropdown
import dev.phonefocus.ui.components.RetroRadio
import dev.phonefocus.ui.components.RetroSunkenPanel
import dev.phonefocus.ui.components.RetroWindow
import dev.phonefocus.ui.icons.RetroIcons
import dev.phonefocus.ui.setup.SetupLabels
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme
import dev.phonefocus.ui.theme.RetroTypography

/** What the user can do in the "Make stricter" dialog. */
class MakeStricterActions(
    val onRuleChange: (packageName: String, rule: AppRule) -> Unit,
    val onExitDelayChange: (ExitDelay) -> Unit,
    val onApply: () -> Unit,
    val onConfirmApply: () -> Unit,
    val onDismissConfirmation: () -> Unit,
    /** Cancel: back to the Focus dialog without changing anything. */
    val onCancel: () -> Unit,
)

/**
 * "Make stricter" (mockup C4, #9), opened from the Focus dialog: a longer exit delay (shorter
 * ones are disabled) and a stricter rule per allowed app, applied with a confirmation. Only
 * offers what [MakeStricterForm] allows; the domain rejects anything looser anyway.
 */
@Composable
fun MakeStricterDialog(
    state: MakeStricterUiState,
    exitPending: Boolean,
    actions: MakeStricterActions,
    appIcon: @Composable (packageName: String) -> Painter,
) {
    Dialog(onDismissRequest = actions.onCancel) {
        RetroWindow(title = "Make stricter", onClose = actions.onCancel) {
            val form = state.form
            if (form == null) {
                Text("Loading…", style = RetroTypography.Body)
                Spacer(Modifier.height(8.dp))
                RetroButton("Cancel", onClick = actions.onCancel, modifier = Modifier.fillMaxWidth())
                return@RetroWindow
            }
            RetroSunkenPanel(Modifier.fillMaxWidth(), background = RetroColors.Highlight) {
                Text(
                    LauncherLabels.STRICTER_WARNING,
                    style = RetroTypography.Body,
                    modifier = Modifier.padding(RetroDimens.Bevel + 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            GroupLabel("Exit delay")
            Column(Modifier.selectableGroup()) {
                form.exitDelayOptions.chunked(2).forEach { row ->
                    Row {
                        row.forEach { choice ->
                            RetroRadio(
                                selected = choice.delay == form.exitDelay,
                                onClick = { actions.onExitDelayChange(choice.delay) },
                                label = SetupLabels.exitDelay(choice.delay),
                                enabled = choice.enabled && !state.applying,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (exitPending) {
                Text(LauncherLabels.STRICTER_PENDING_NOTE, style = RetroTypography.Label)
            }
            Spacer(Modifier.height(8.dp))
            GroupLabel("Allowed apps")
            if (form.apps.isEmpty()) {
                Text(LauncherLabels.STRICTER_NO_APPS, style = RetroTypography.Body)
            } else {
                RetroSunkenPanel(Modifier.fillMaxWidth().heightIn(max = 260.dp), background = RetroColors.Surface) {
                    LazyColumn(Modifier.padding(RetroDimens.Bevel + 4.dp)) {
                        items(form.apps, key = { it.packageName }) { choice ->
                            StricterAppRow(choice, !state.applying, { actions.onRuleChange(choice.packageName, it) }, appIcon)
                        }
                    }
                }
            }
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = RetroTypography.Label)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RetroButton("Cancel", onClick = actions.onCancel, modifier = Modifier.weight(1f))
                RetroButton(
                    "Apply…",
                    onClick = actions.onApply,
                    enabled = state.canApply,
                    isDefault = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (state.confirming && state.form != null) {
        ConfirmStricterDialog(state.form, actions.onConfirmApply, actions.onDismissConfirmation)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = RetroTypography.Title, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun StricterAppRow(
    choice: StricterAppChoice,
    enabled: Boolean,
    onRuleChange: (AppRule) -> Unit,
    appIcon: @Composable (String) -> Painter,
) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = RetroDimens.MinTouchTarget).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(appIcon(choice.packageName), contentDescription = null, modifier = Modifier.size(24.dp))
        Text(
            choice.app.label,
            style = RetroTypography.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RetroDropdown(
            options = choice.options,
            selected = choice.rule,
            onSelect = onRuleChange,
            enabled = enabled && choice.options.size > 1,
            optionLabel = SetupLabels::rule,
            isHighlighted = { it == AppRule.ShortVideoBlocked },
            modifier = Modifier.width(168.dp),
        )
    }
}

@Composable
private fun ConfirmStricterDialog(form: MakeStricterForm, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        RetroWindow(title = "Make stricter?", onClose = onDismiss) {
            Text(LauncherLabels.stricterConfirmation(form), style = RetroTypography.Body)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RetroButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                RetroButton("Apply", onClick = onConfirm, isDefault = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun previewForm(): MakeStricterForm {
    val apps = listOf(
        InstalledApp("com.instagram.android", "Instagram"),
        InstalledApp("com.example.maps", "Maps"),
        InstalledApp("com.google.android.youtube", "YouTube"),
    )
    val catalogue = apps.map { AppCatalogueEntry(it, AppRuleOptions.forPackage(it.packageName, EssentialApps.NONE), false) }
    val policy = AppPolicy(
        mapOf(
            "com.instagram.android" to AppRule.Allowed,
            "com.example.maps" to AppRule.Allowed,
            "com.google.android.youtube" to AppRule.ShortVideoBlocked,
        ),
    )
    val session = (FocusSession.initial().startFocus(policy, ExitDelay.ONE_DAY) as FocusResult.Success).session
    return MakeStricterForm.create(
        catalogue,
        session,
        ExitDelayOptions.available(debugBuild = true),
        ruleEdits = mapOf("com.instagram.android" to AppRule.ShortVideoBlocked),
    )
}

private val previewStricterActions = MakeStricterActions({ _, _ -> }, {}, {}, {}, {}, {})

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun MakeStricterDialogPreview() {
    RetroTheme {
        val placeholder = rememberVectorPainter(RetroIcons.AppPlaceholder)
        MakeStricterDialog(MakeStricterUiState(previewForm()), exitPending = false, previewStricterActions) { placeholder }
    }
}
