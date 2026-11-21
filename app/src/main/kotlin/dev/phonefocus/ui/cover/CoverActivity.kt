package dev.phonefocus.ui.cover

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.phonefocus.application.CoverScreen
import dev.phonefocus.application.QuickAction
import dev.phonefocus.infrastructure.AppContainer
import dev.phonefocus.infrastructure.PhoneFocusApp
import dev.phonefocus.infrastructure.cover.CoverDisplay
import dev.phonefocus.infrastructure.cover.CoverIntents
import dev.phonefocus.ui.components.RetroButton
import dev.phonefocus.ui.components.RetroWindow
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroTheme
import dev.phonefocus.ui.theme.RetroTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * The cover screen (#13), opened from the cover widget on the Flex Window. Shows the clock, the
 * focus status and the quick buttons; a quick button opens its app on the same display.
 *
 * Every quick action, from the widget or from here, is checked with [AppAccess][dev.phonefocus.domain.AppAccess]
 * at the moment of the tap ([dev.phonefocus.application.CheckQuickAction]), so a stale widget
 * never opens a blocked app. A widget tap for an allowed app opens it right away and closes this
 * screen; a blocked one shows this screen with a notice.
 */
class CoverActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as PhoneFocusApp).container

    private var notice by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            RetroTheme {
                val screen by container.observeCoverScreen().collectAsStateWithLifecycle(initialValue = null)
                CoverContent(screen, notice, onQuickAction = { open(it, finishAfter = false) })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        notice = null
        CoverIntents.quickActionOf(intent)?.let { open(it, finishAfter = true) }
    }

    /** Opens [action] on this screen's display if [AppAccess][dev.phonefocus.domain.AppAccess] allows it now. */
    private fun open(action: QuickAction, finishAfter: Boolean) {
        lifecycleScope.launch {
            val packageName = withContext(Dispatchers.IO) { container.checkQuickAction(action) }
            if (packageName == null) {
                notice = CoverLabels.blocked(action)
                return@launch
            }
            whenUnlocked {
                val options = display?.let { CoverDisplay.launchOptions(this@CoverActivity, it.displayId) }
                if (container.quickActions.launch(this@CoverActivity, action, packageName, options)) {
                    if (finishAfter) finish()
                } else {
                    notice = CoverLabels.cannotOpen(action)
                }
            }
        }
    }

    /** Runs [block] once the keyguard is gone (asks to unlock first when the phone is locked). */
    private fun whenUnlocked(block: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            block()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = block()
            },
        )
    }
}

/** Texts of the cover screen. */
internal object CoverLabels {
    const val TITLE = "Phone Focus"
    fun blocked(action: QuickAction) = "${action.label} is blocked during focus."
    fun cannotOpen(action: QuickAction) = "${action.label} could not be opened."
}

@Composable
private fun CoverContent(screen: CoverScreen?, notice: String?, onQuickAction: (QuickAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RetroColors.Desktop)
            .safeDrawingPadding()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RetroWindow(title = CoverLabels.TITLE, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Clock()
                Text(screen?.status?.text.orEmpty(), style = RetroTypography.Title)
                if (notice != null) {
                    Text(notice, style = RetroTypography.Label, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        // The bottom-left corner stays empty: the camera cutouts.
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(2f))
            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                screen?.buttons.orEmpty().chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { button ->
                            RetroButton(
                                onClick = { onQuickAction(button.action) },
                                enabled = button.enabled,
                                modifier = Modifier.weight(1f),
                            ) { Text(button.action.label, style = RetroTypography.Label, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

/** The time, updated on every minute. */
@Composable
private fun Clock() {
    val context = LocalContext.current
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay((60 - now.second) * 1000L - now.nano / 1_000_000)
        }
    }
    Box(contentAlignment = Alignment.Center) {
        Text(now.format(formatter), style = RetroTypography.Countdown)
    }
}
