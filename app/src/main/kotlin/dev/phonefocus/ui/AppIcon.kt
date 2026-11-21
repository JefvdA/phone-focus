package dev.phonefocus.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.phonefocus.infrastructure.apps.AppIconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The launcher icon of [packageName], loaded off the main thread; empty space until loaded. */
@Composable
fun AppIcon(packageName: String, loader: AppIconLoader, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val icon by produceState<ImageBitmap?>(null, packageName, loader) {
        value = withContext(Dispatchers.IO) { loader.load(packageName)?.toBitmap(48, 48)?.asImageBitmap() }
    }
    icon?.let { Image(it, contentDescription = null, modifier = modifier.size(size)) }
        ?: Spacer(modifier.size(size))
}
