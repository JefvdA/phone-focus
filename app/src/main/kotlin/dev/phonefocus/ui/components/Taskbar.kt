package dev.phonefocus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.phonefocus.ui.icons.RetroIcons
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme
import dev.phonefocus.ui.theme.RetroTypography

/**
 * Grey bar along the bottom of the desktop: a menu button on the left, [content] (usually
 * [TaskbarButton]s) in the middle and an optional sunken tray with [trayText] (e.g. a clock or
 * the countdown) on the right.
 */
@Composable
fun Taskbar(
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    menuLabel: String = "Menu",
    trayText: String? = null,
    content: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(RetroColors.Surface)) {
        // Classic raised top edge: grey line then white line.
        Box(Modifier.fillMaxWidth().height(RetroDimens.BevelLine).background(RetroColors.Surface))
        Box(Modifier.fillMaxWidth().height(RetroDimens.BevelLine).background(RetroColors.BevelLight))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = RetroDimens.TaskbarMinHeight - RetroDimens.Bevel)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RetroButton(onClick = onMenuClick, modifier = Modifier.defaultMinSize(minWidth = 0.dp)) {
                Image(RetroIcons.Menu, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(menuLabel, style = RetroTypography.Body.copy(fontWeight = FontWeight.Bold), color = LocalContentColor.current)
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
            if (trayText != null) {
                RetroSunkenPanel(
                    modifier = Modifier.defaultMinSize(minHeight = RetroDimens.MinTouchTarget),
                    background = RetroColors.Surface,
                ) {
                    Text(
                        trayText,
                        style = RetroTypography.Label,
                        color = RetroColors.Text,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

/** A window's button on the [Taskbar]; [active] shows it pressed in. */
@Composable
fun TaskbarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    icon: ImageVector? = null,
) {
    RetroButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 0.dp),
        latched = active,
    ) {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(text, style = RetroTypography.Label, color = LocalContentColor.current, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF007878, widthDp = 360)
@Composable
private fun TaskbarPreview() {
    RetroTheme {
        Column {
            Spacer(Modifier.height(24.dp))
            Taskbar(onMenuClick = {}, trayText = "12:00") {
                TaskbarButton("Focus", onClick = {}, active = true, icon = RetroIcons.Lock)
            }
        }
    }
}
