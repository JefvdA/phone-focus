package dev.phonefocus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.phonefocus.ui.icons.RetroIcons
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme
import dev.phonefocus.ui.theme.RetroTypography

/**
 * Desktop shortcut: a pixel icon with a white label below, for the teal desktop. When
 * [selected], the label is shown in white on navy. At least [RetroDimens.DesktopIconMinWidth]
 * by [RetroDimens.DesktopIconMinHeight].
 */
@Composable
fun DesktopIcon(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = RetroDimens.DesktopIconMinWidth, minHeight = RetroDimens.DesktopIconMinHeight)
            .widthIn(max = RetroDimens.DesktopIconMinWidth + 16.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Text(
            text = label,
            style = RetroTypography.Label,
            color = if (selected) RetroColors.OnTitleBar else RetroColors.OnDesktop,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = (if (selected) Modifier.background(RetroColors.TitleBar) else Modifier)
                .padding(horizontal = 2.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF007878)
@Composable
private fun DesktopIconPreview() {
    RetroTheme {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopIcon(rememberVectorPainter(RetroIcons.Lock), "Focus", onClick = {})
            DesktopIcon(rememberVectorPainter(RetroIcons.AppPlaceholder), "Calculator", onClick = {}, selected = true)
            DesktopIcon(rememberVectorPainter(RetroIcons.AppPlaceholder), "A very long app name here", onClick = {})
        }
    }
}
