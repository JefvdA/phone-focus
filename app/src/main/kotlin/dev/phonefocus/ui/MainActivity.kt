package dev.phonefocus.ui

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/** Opened from the app icon in One UI: the focus setup screen (C2, #7). */
class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as PhoneFocusApp).container

    private val viewModel: FocusSetupViewModel by viewModels { FocusSetupViewModel.factory(container) }

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
                        onStart = viewModel::requestStart,
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

    private fun openSettings(prerequisite: StartPrerequisite) {
        try {
            startActivity(AndroidStartPrerequisites.settingsIntent(prerequisite))
        } catch (e: ActivityNotFoundException) {
            Log.w("MainActivity", "no settings screen for $prerequisite", e)
        }
    }

    private fun openFocusLauncher() {
        // TODO(#8): switch to the focus launcher (retro desktop). Until it exists the setup
        //  screen stays open and shows that focus mode is on.
    }
}
