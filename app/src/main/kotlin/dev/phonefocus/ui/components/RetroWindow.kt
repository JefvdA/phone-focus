package dev.phonefocus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.phonefocus.ui.icons.RetroIcons
import dev.phonefocus.ui.theme.BevelStyle
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme
import dev.phonefocus.ui.theme.RetroTypography
import dev.phonefocus.ui.theme.retroBevel

/**
 * A 90s window: raised grey frame with a navy title bar. Shows a close button when [onClose]
 * is set (touch target [RetroDimens.CloseButtonTouchTarget]).
 */
@Composable
fun RetroWindow(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    closeContentDescription: String = "Close",
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.retroBevel(BevelStyle.Raised)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = RetroDimens.TitleBarMinHeight)
                .background(RetroColors.TitleBar)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = RetroTypography.Title,
                color = RetroColors.OnTitleBar,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onClose != null) WindowCloseButton(onClose, closeContentDescription)
        }
        Column(Modifier.padding(RetroDimens.ContentPadding), content = content)
    }
}

/** Small raised close box centred in a full-size touch target. */
@Composable
private fun WindowCloseButton(onClick: () -> Unit, contentDescription: String) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shift = if (pressed) RetroDimens.BevelLine / 2 else 0.dp
    Box(
        modifier = Modifier
            .size(RetroDimens.CloseButtonTouchTarget)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 32.dp, height = 28.dp)
                .retroBevel(if (pressed) BevelStyle.Pressed else BevelStyle.Raised),
            contentAlignment = Alignment.Center,
        ) {
            Image(RetroIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp, 14.dp).offset(shift, shift))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF007878)
@Composable
private fun RetroWindowPreview() {
    RetroTheme {
        RetroWindow(title = "Focus settings", onClose = {}, modifier = Modifier.padding(16.dp)) {
            Text("Window content goes here.", style = RetroTypography.Body)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF007878)
@Composable
private fun RetroWindowNoClosePreview() {
    RetroTheme {
        RetroWindow(title = "A very long window title that does not fit", modifier = Modifier.padding(16.dp)) {
            Text("No close button.", style = RetroTypography.Body)
        }
    }
}
