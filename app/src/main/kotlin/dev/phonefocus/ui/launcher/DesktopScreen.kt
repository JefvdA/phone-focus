package dev.phonefocus.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.phonefocus.application.FocusDesktop
import dev.phonefocus.application.InstalledApp
import dev.phonefocus.domain.AppPolicy
import dev.phonefocus.domain.AppRule
import dev.phonefocus.domain.EssentialApps
import dev.phonefocus.domain.FocusResult
import dev.phonefocus.domain.FocusSession
import dev.phonefocus.domain.FocusState
import dev.phonefocus.ui.components.DesktopIcon
import dev.phonefocus.ui.components.RetroButton
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
)

/**
 * The focus launcher (C3, #8): teal desktop with the allowed apps and a taskbar with Menu,
 * Focus button (the remaining time while an exit countdown runs) and a clock. While Unlocked
 * it shows "Focus is off" with a way back to One UI Home instead of apps.
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
            FocusDialog(desktop.session, state.remaining, actions.onCloseDialog)
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

/** The Focus dialog, opened from the taskbar: state, exit delay and the ways to change it. */
@Composable
private fun FocusDialog(session: FocusSession, remaining: Duration?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        RetroWindow(title = "Focus", onClose = onClose) {
            Text(LauncherLabels.FOCUS_ON, style = RetroTypography.Body)
            Spacer(Modifier.height(4.dp))
            Text(LauncherLabels.exitDelay(session.exitDelay), style = RetroTypography.Body)
            if (session.state is FocusState.ExitPending && remaining != null) {
                Spacer(Modifier.height(4.dp))
                Text(LauncherLabels.exitPending(remaining), style = RetroTypography.Body)
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // TODO(#9): open the "Make stricter" dialog.
                RetroButton("Make stricter…", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
                // TODO(#10): open the emergency escape / exit countdown flow.
                RetroButton("Emergency escape…", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
                RetroButton("Close", onClick = onClose, isDefault = true, modifier = Modifier.fillMaxWidth())
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

private val previewActions = LauncherActions({}, {}, {}, {}, {})

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
            LauncherUiState(previewDesktop(state), remaining = Duration.ofMinutes(23 * 60 + 41)),
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
