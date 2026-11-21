package dev.phonefocus.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.phonefocus.application.PrerequisiteStatus
import dev.phonefocus.application.StartPrerequisite
import dev.phonefocus.domain.AppVersion
import dev.phonefocus.infrastructure.AppContainer
import dev.phonefocus.infrastructure.PhoneFocusApp
import dev.phonefocus.infrastructure.system.AndroidStartPrerequisites
import dev.phonefocus.ui.setup.FocusSetupActions
import dev.phonefocus.ui.setup.FocusSetupEvent
import dev.phonefocus.ui.setup.FocusSetupScreen
import dev.phonefocus.ui.setup.FocusSetupViewModel
import dev.phonefocus.ui.theme.RetroTheme
import kotlinx.coroutines.launch

/** Opened from the app icon in One UI: the focus setup screen (C2, #7). */
class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as PhoneFocusApp).container

    private val viewModel: FocusSetupViewModel by viewModels { FocusSetupViewModel.factory(container) }

    // Optional: without it the "Focus ended" notification (#10) is skipped, nothing else changes.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.i("MainActivity", "notification permission granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = container
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        setContent {
            RetroTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LifecycleResumeEffect(Unit) {
                    // The user may come back from the system settings with a prerequisite set up.
                    viewModel.refreshPrerequisites()
                    onPauseOrDispose {}
                }
                LaunchedEffect(Unit) {
                    viewModel.oneTimeEvents.collect { event ->
                        when (event) {
                            FocusSetupEvent.FocusStarted -> openFocusLauncher()
                        }
                    }
                }
                val actions = remember {
                    FocusSetupActions(
                        onRuleChange = viewModel::setRule,
                        onExitDelayChange = viewModel::setExitDelay,
                        onStart = {
                            askForNotifications()
                            viewModel.requestStart()
                        },
                        onConfirmStart = viewModel::confirmStart,
                        onDismissConfirmation = viewModel::dismissConfirmation,
                        onOpenSettings = ::openSettings,
                        // Back to One UI.
                        onCancel = ::finish,
                    )
                }
                FocusSetupScreen(
                    state = state,
                    actions = actions,
                    appIcon = { AppIcon(it, container.appIcons) },
                    debugContent = if (container.isDebugBuild) {
                        { DebugFocusPanel(container, AppVersion.describe(versionName), state.prerequisites) }
                    } else {
                        null
                    },
                )
            }
        }
    }

    /**
     * Asks once per start for permission to post notifications, so the user hears when focus
     * ends (#10). A refusal is respected: the notification is then skipped silently.
     */
    private fun askForNotifications() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openSettings(prerequisite: StartPrerequisite) {
        try {
            startActivity(AndroidStartPrerequisites.settingsIntent(prerequisite))
        } catch (e: ActivityNotFoundException) {
            Log.w("MainActivity", "no settings screen for $prerequisite", e)
        }
    }

    /**
     * Focus has started: show the focus launcher. v1 cannot set the default home app itself
     * (#14 automates this), so if this app is not the default home yet, the system "Default
     * home app" settings open for the user to pick it.
     */
    private fun openFocusLauncher() {
        lifecycleScope.launch {
            val home = container.startPrerequisites.status(StartPrerequisite.DefaultHome)
            if (home == PrerequisiteStatus.Missing) {
                openSettings(StartPrerequisite.DefaultHome)
            } else {
                startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                finish()
            }
        }
    }
}
