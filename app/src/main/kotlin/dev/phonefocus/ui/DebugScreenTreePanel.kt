package dev.phonefocus.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.phonefocus.infrastructure.system.ScreenTreeDebug

/**
 * Debug builds only (#12): turns the app blocker's screen tree dump on or off and shows the
 * latest tree per app (also in logcat, tag `FocusScreenTree`). Turn it on, open the
 * short-video screen in the other app, come back here and read its view ids. Needs the
 * accessibility service to be on.
 */
@Composable
fun DebugScreenTreePanel(debug: ScreenTreeDebug) {
    val enabled by debug.enabled.collectAsState()
    val snapshots by debug.snapshots.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Screen tree dump", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = enabled, onCheckedChange = debug::setEnabled)
            Button(onClick = debug::clear) { Text("Clear") }
        }
        if (snapshots.isEmpty()) {
            Text(
                if (enabled) "Open another app; its tree appears here." else "Off.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        snapshots.values.reversed().forEach { snapshot ->
            Text(
                "${snapshot.packageName} — short video: ${snapshot.matchedRule ?: "no"} (${snapshot.lines.size} nodes)",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = snapshot.lines.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .border(1.dp, Color.Gray)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp),
            )
        }
    }
}
