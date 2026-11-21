package dev.phonefocus.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.phonefocus.application.ExitCountdownStatus
import dev.phonefocus.application.FocusDesktop
import dev.phonefocus.application.InstalledApp
import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.ExitDelay
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.FocusState
import dev.phonefocus.ui.components.DesktopIcon
import dev.phonefocus.ui.components.RetroButton
import dev.phonefocus.ui.components.RetroProgressBar
import dev.phonefocus.ui.components.RetroWindow
import dev.phonefocus.ui.components.Taskbar
import dev.phonefocus.ui.components.TaskbarButton
import dev.phonefocus.ui.icons.RetroIcons
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme
import dev.phonefocus.ui.theme.RetroTypography
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** What the user can do on the desktop. */
class LauncherActions(
    val onOpenApp: (packageName: String) -> Unit,
    val onOpenDialog: (LauncherDialog) -> Unit,
    val onCloseDialog: () -> Unit,
    /** The focus setup screen (C2). */
    val onOpenSetup: () -> Unit,
    /** The system "Default home app" settings, to switch back to One UI Home. */
    val onSwitchHomeBack: () -> Unit,
    /** The "Make stricter" dialog (C4). */
    val stricter: MakeStricterActions,
    /** Emergency escape confirmed: start the exit countdown (C5). */
    val onConfirmEmergencyEscape: () -> Unit,
    /** "Cancel exit, stay focused" (C5). */
    val onCancelExit: () -> Unit,
)

/**
 * The focus launcher (C3, #8): teal desktop with the allowed apps and a taskbar with Menu,
 * Focus button (the remaining time while an exit countdown runs) and a clock. While Unlocked
 * it shows "Focus is off" with a way back to One UI Home instead of apps. The Focus button opens
 * the Focus dialog, or the exit-pending dialog (C5, #10) while an exit countdown runs.
 */
@Composable
fun DesktopScreen(
    state: LauncherUiState,
    actions: LauncherActions,
    appIcon: @Composable (packageName: String) -> Painter,
) {
    val desktop = state.desktop
    val focusActive = desktop?.session?.state?.isFocusActive == true
    Column(Modifier.fillMaxSize().background(RetroColors.Desktop)) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            when {
                desktop == null -> Unit
                focusActive -> AppGrid(desktop.apps, actions.onOpenApp, appIcon)
                else -> FocusOffWindow(actions, Modifier.align(Alignment.Center).padding(16.dp))
            }
        }
        Box(Modifier.background(RetroColors.Surface).navigationBarsPadding()) {
            Taskbar(onMenuClick = { actions.onOpenDialog(LauncherDialog.Menu) }, trayText = clockText()) {
                Spacer(Modifier.weight(1f))
                if (focusActive) {
                    TaskbarButton(
                        text = LauncherLabels.focusButton(state.remaining),
                        onClick = { actions.onOpenDialog(LauncherDialog.Focus) },
                        active = state.dialog == LauncherDialog.Focus || state.remaining != null,
                        icon = if (state.remaining == null) RetroIcons.Lock else null,
                    )
                }
            }
        }
    }
    when (state.dialog) {
        LauncherDialog.Menu -> MenuDialog(focusActive, actions)
        LauncherDialog.Focus -> if (desktop != null && focusActive) {
            val countdown = state.countdown
            if (desktop.session.state is FocusState.ExitPending && countdown != null) {
                ExitPendingDialog(
                    countdown,
                    state.exit,
                    onCancelExit = actions.onCancelExit,
                    onMakeStricter = { actions.onOpenDialog(LauncherDialog.MakeStricter) },
                    onClose = actions.onCloseDialog,
                )
            } else if (desktop.session.state == FocusState.Focused) {
                FocusDialog(
                    desktop.session,
                    onMakeStricter = { actions.onOpenDialog(LauncherDialog.MakeStricter) },
                    onEmergencyEscape = { actions.onOpenDialog(LauncherDialog.EmergencyEscape) },
                    onClose = actions.onCloseDialog,
                )
            }
        }
        LauncherDialog.EmergencyEscape -> if (desktop != null && desktop.session.state == FocusState.Focused) {
            EmergencyEscapeDialog(
                desktop.session.exitDelay,
                state.exit,
                onConfirm = actions.onConfirmEmergencyEscape,
                onCancel = { actions.onOpenDialog(LauncherDialog.Focus) },
            )
        }
        LauncherDialog.MakeStricter -> if (desktop != null && focusActive) {
            MakeStricterDialog(state.stricter, desktop.session.state is FocusState.ExitPending, actions.stricter, appIcon)
        }
        null -> Unit
    }
}

@Composable
private fun AppGrid(
    apps: List<InstalledApp>,
    onOpenApp: (String) -> Unit,
    appIcon: @Composable (String) -> Painter,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(RetroDimens.DesktopIconMinWidth + 16.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(apps, key = { it.packageName }) { app ->
            Box(contentAlignment = Alignment.TopCenter) {
                DesktopIcon(icon = appIcon(app.packageName), label = app.label, onClick = { onOpenApp(app.packageName) })
            }
        }
    }
}

/** While Unlocked the desktop must not trap the user: a way back to One UI Home and to setup. */
@Composable
private fun FocusOffWindow(actions: LauncherActions, modifier: Modifier = Modifier) {
    RetroWindow(title = "Focus is off", modifier = modifier) {
        Text(LauncherLabels.FOCUS_OFF, style = RetroTypography.Body)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroButton("Switch home back…", onClick = actions.onSwitchHomeBack, isDefault = true, modifier = Modifier.fillMaxWidth())
            RetroButton("Open focus setup", onClick = actions.onOpenSetup, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The Focus dialog, opened from the taskbar while focused: exit delay and the ways to change it. */
@Composable
private fun FocusDialog(session: FocusSession, onMakeStricter: () -> Unit, onEmergencyEscape: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        RetroWindow(title = "Focus", onClose = onClose) {
            Text(LauncherLabels.FOCUS_ON, style = RetroTypography.Body)
            Spacer(Modifier.height(4.dp))
            Text(LauncherLabels.exitDelay(session.exitDelay), style = RetroTypography.Body)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RetroButton("Make stricter…", onClick = onMakeStricter, modifier = Modifier.fillMaxWidth())
                RetroButton("Emergency escape…", onClick = onEmergencyEscape, modifier = Modifier.fillMaxWidth())
                RetroButton("Close", onClick = onClose, isDefault = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Confirmation before the exit countdown starts, with the session's actual exit delay. */
@Composable
private fun EmergencyEscapeDialog(delay: ExitDelay, exit: ExitUiState, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        RetroWindow(title = "Emergency escape", onClose = onCancel) {
            Text(LauncherLabels.emergencyConfirmation(delay), style = RetroTypography.Body)
            exit.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = RetroTypography.Body)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Staying focused is the default; starting the countdown takes a deliberate tap.
                RetroButton("Cancel", onClick = onCancel, isDefault = true, modifier = Modifier.weight(1f))
                RetroButton("Continue", onClick = onConfirm, enabled = !exit.busy, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * The exit-pending dialog (C5): the time left as hh:mm:ss, a progress bar of the elapsed part
 * of the countdown and a way to cancel the exit.
 */
@Composable
private fun ExitPendingDialog(
    countdown: ExitCountdownStatus,
    exit: ExitUiState,
    onCancelExit: () -> Unit,
    onMakeStricter: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        RetroWindow(title = "Exit pending", onClose = onClose) {
            Text(LauncherLabels.EXIT_PENDING_INTRO, style = RetroTypography.Body)
            Spacer(Modifier.height(8.dp))
            Text(
                LauncherLabels.exitCountdown(countdown.remaining),
                style = RetroTypography.Countdown,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            RetroProgressBar(countdown.elapsedFraction, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(LauncherLabels.EXIT_PENDING_NOTE, style = RetroTypography.Body)
            exit.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = RetroTypography.Body)
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RetroButton(
                    "Cancel exit, stay focused",
                    onClick = onCancelExit,
                    enabled = !exit.busy,
                    isDefault = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                RetroButton("Make stricter…", onClick = onMakeStricter, modifier = Modifier.fillMaxWidth())
                RetroButton("Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MenuDialog(focusActive: Boolean, actions: LauncherActions) {
    Dialog(onDismissRequest = actions.onCloseDialog) {
        RetroWindow(title = "Menu", onClose = actions.onCloseDialog) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (focusActive) {
                    RetroButton("Focus mode…", onClick = { actions.onOpenDialog(LauncherDialog.Focus) }, modifier = Modifier.fillMaxWidth())
                }
                RetroButton("Focus setup", onClick = actions.onOpenSetup, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** The local time for the taskbar tray, in the device's 12/24-hour format, updated every minute. */
@Composable
private fun clockText(): String {
    val context = LocalContext.current
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    val formatter = DateTimeFormatter.ofPattern(pattern)
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay((60 - value.second) * 1_000L - value.nano / 1_000_000L)
        }
    }
    return formatter.format(now)
}

private fun previewDesktop(state: FocusState): FocusDesktop {
    val apps = listOf(
        InstalledApp("com.example.phone", "Phone"),
        InstalledApp("com.example.messages", "Messages"),
        InstalledApp("com.example.camera", "Camera"),
        InstalledApp("com.example.maps", "Maps"),
        InstalledApp("com.android.settings", "Settings"),
    )
    val policy = AppPolicy(apps.associate { it.packageName to AppRule.Allowed })
    var session = FocusSession.initial()
    if (state.isFocusActive) session = (session.startFocus(policy, session.exitDelay) as FocusResult.Success).session
    if (state is FocusState.ExitPending) {
        session = (session.requestExit(state.unlockAt.minus(session.exitDelay.duration)) as FocusResult.Success).session
    }
    return FocusDesktop.of(session, apps, EssentialApps.NONE, "dev.phonefocus")
}

private val previewActions = LauncherActions({}, {}, {}, {}, {}, MakeStricterActions({ _, _ -> }, {}, {}, {}, {}, {}), {}, {})

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun DesktopFocusedPreview() {
    RetroTheme {
        val placeholder = rememberVectorPainter(RetroIcons.AppPlaceholder)
        DesktopScreen(LauncherUiState(previewDesktop(FocusState.Focused)), previewActions) { placeholder }
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun DesktopExitPendingPreview() {
    RetroTheme {
        val placeholder = rememberVectorPainter(RetroIcons.AppPlaceholder)
        val state = FocusState.ExitPending(java.time.Instant.parse("2026-01-02T00:00:00Z"))
        DesktopScreen(
            LauncherUiState(
                previewDesktop(state),
                countdown = ExitCountdownStatus(Duration.ofMinutes(23 * 60 + 41).plusSeconds(7), Duration.ofHours(24)),
            ),
            previewActions,
        ) { placeholder }
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun ExitPendingDialogPreview() {
    RetroTheme {
        val placeholder = rememberVectorPainter(RetroIcons.AppPlaceholder)
        val state = FocusState.ExitPending(java.time.Instant.parse("2026-01-02T00:00:00Z"))
        DesktopScreen(
            LauncherUiState(
                previewDesktop(state),
                countdown = ExitCountdownStatus(Duration.ofHours(15).plusMinutes(12).plusSeconds(30), Duration.ofHours(24)),
                dialog = LauncherDialog.Focus,
            ),
            previewActions,
        ) { placeholder }
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun EmergencyEscapePreview() {
    RetroTheme {
        val placeholder = rememberVectorPainter(RetroIcons.AppPlaceholder)
        DesktopScreen(
            LauncherUiState(previewDesktop(FocusState.Focused), dialog = LauncherDialog.EmergencyEscape),
            previewActions,
        ) { placeholder }
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun DesktopUnlockedPreview() {
    RetroTheme {
        val placeholder = rememberVectorPainter(RetroIcons.AppPlaceholder)
        DesktopScreen(LauncherUiState(previewDesktop(FocusState.Unlocked)), previewActions) { placeholder }
    }
}
