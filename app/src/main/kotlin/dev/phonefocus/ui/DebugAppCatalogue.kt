package dev.phonefocus.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.phonefocus.application.AppCatalogueEntry
import dev.phonefocus.infrastructure.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Debug builds only: the app catalogue (#5), to check on the device that every app from the
 * app drawer is listed and which rule options each gets. Replaced by the setup screen (#7).
 */
@Composable
fun DebugAppCatalogue(container: AppContainer) {
    val catalogue = remember(container) { container.observeAppCatalogue() }
    val entries by catalogue.collectAsState(initial = null)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = entries?.let { "Debug: ${it.size} apps" } ?: "Loading apps…",
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyColumn(modifier = Modifier.heightIn(max = 280.dp).padding(horizontal = 16.dp)) {
            items(entries.orEmpty(), key = { it.app.packageName }) { DebugAppRow(container, it) }
        }
    }
}

@Composable
private fun DebugAppRow(container: AppContainer, entry: AppCatalogueEntry) {
    val packageName = entry.app.packageName
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) { container.appIcons.load(packageName)?.toBitmap(48, 48)?.asImageBitmap() }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(24.dp)) } ?: Spacer(Modifier.size(24.dp))
        Column {
            Text(entry.app.label, style = MaterialTheme.typography.bodyMedium)
            val options = if (entry.essential) "always allowed" else entry.ruleOptions.joinToString(" / ")
            Text("$packageName · $options", style = MaterialTheme.typography.bodySmall)
        }
    }
}
