package dev.phonefocus.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.phonefocus.domain.AppVersion
import dev.phonefocus.infrastructure.PhoneFocusApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        val container = (application as PhoneFocusApp).container
        setContent {
            MaterialTheme {
                PlaceholderScreen(AppVersion.describe(versionName)) {
                    if (container.isDebugBuild) DebugFocusPanel(container)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(label: String, extra: @Composable () -> Unit = {}) {
    Scaffold { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(text = label, style = MaterialTheme.typography.headlineSmall)
                extra()
            }
        }
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    MaterialTheme { PlaceholderScreen("phone-focus 0.1.0") }
}
