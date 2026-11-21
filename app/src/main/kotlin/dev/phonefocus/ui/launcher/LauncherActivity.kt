package dev.phonefocus.ui.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.phonefocus.application.StartPrerequisite
import dev.phonefocus.infrastructure.AppContainer
import dev.phonefocus.infrastructure.PhoneFocusApp
import dev.phonefocus.infrastructure.system.AndroidStartPrerequisites
import dev.phonefocus.infrastructure.system.FocusAccessibilityService
import dev.phonefocus.ui.MainActivity
import dev.phonefocus.ui.rememberAppIconPainter
import dev.phonefocus.ui.theme.RetroTheme

/**
 * The focus launcher (C3, #8): this app's `HOME` activity. Back never leaves the desktop (it
 * only closes an open window) and Home brings the plain desktop back.
 */
class LauncherActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as PhoneFocusApp).container

    private val viewModel: LauncherViewModel by viewModels { LauncherViewModel.factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) showNoticeIfBlocked(intent)
        // A home screen has nothing to go back to: back only closes a window.
        onBackPressedDispatcher.addCallback(this) { viewModel.closeDialog() }
        val icons = container.appIcons
        setContent {
            RetroTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LifecycleResumeEffect(Unit) {
                    // An ended countdown unlocks even if the alarm was late.
                    viewModel.tick()
                    // The app blocker may have been turned off while the desktop was away.
                    viewModel.refreshBlockerStatus()
                    onPauseOrDispose {}
                }
                val actions = remember {
                    LauncherActions(
                        onOpenApp = viewModel::open,
                        onOpenDialog = viewModel::openDialog,
                        onCloseDialog = viewModel::closeDialog,
                        onOpenSetup = ::openSetup,
                        onSwitchHomeBack = ::openHomeSettings,
                        onOpenBlockerSettings = ::openAccessibilitySettings,
                        stricter = MakeStricterActions(
                            onRuleChange = viewModel::setStricterRule,
                            onExitDelayChange = viewModel::setStricterDelay,
                            onApply = viewModel::requestApplyStricter,
                            onConfirmApply = viewModel::confirmApplyStricter,
                            onDismissConfirmation = viewModel::dismissStricterConfirmation,
                            onCancel = { viewModel.openDialog(LauncherDialog.Focus) },
                        ),
                        onConfirmEmergencyEscape = viewModel::confirmEmergencyEscape,
                        onCancelExit = viewModel::cancelExit,
                    )
                }
                DesktopScreen(state, actions) { rememberAppIconPainter(it, icons) }
            }
        }
    }

    /** Home pressed while the desktop is in front: show the plain desktop. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.closeDialog()
        showNoticeIfBlocked(intent)
    }

    /** Sent back here by the app blocker (#11): say why. */
    private fun showNoticeIfBlocked(intent: Intent?) {
        if (intent?.getBooleanExtra(FocusAccessibilityService.EXTRA_BLOCKED, false) == true) {
            viewModel.showBlockedNotice()
        }
    }

    private fun openSetup() {
        viewModel.closeDialog()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openHomeSettings() = openSettings(StartPrerequisite.DefaultHome)

    private fun openAccessibilitySettings() = openSettings(StartPrerequisite.AccessibilityService)

    private fun openSettings(prerequisite: StartPrerequisite) {
        try {
            startActivity(AndroidStartPrerequisites.settingsIntent(prerequisite))
        } catch (e: ActivityNotFoundException) {
            Log.w("LauncherActivity", "no settings screen for $prerequisite", e)
        }
    }
}
