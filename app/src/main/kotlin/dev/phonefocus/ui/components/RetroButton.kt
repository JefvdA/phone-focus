package dev.phonefocus.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.phonefocus.ui.theme.BevelStyle
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme
import dev.phonefocus.ui.theme.RetroTypography
import dev.phonefocus.ui.theme.retroBevel

/**
 * Raised 90s push button. While pressed (or when [latched]) the bevel is inverted and the
 * content shifts one bevel line down-right. A [isDefault] button (the one Enter would press)
 * gets a 1px black outline. At least [RetroDimens.MinTouchTarget] tall.
 */
@Composable
fun RetroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDefault: Boolean = false,
    latched: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val sunk = latched || (pressed && enabled)
    val shift = if (sunk) RetroDimens.BevelLine / 2 else 0.dp
    val outline = if (isDefault) {
        Modifier.border(RetroDimens.DefaultButtonOutline, RetroColors.Text, RectangleShape)
            .padding(RetroDimens.DefaultButtonOutline)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = RetroDimens.ButtonMinWidth, minHeight = RetroDimens.ButtonMinHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(outline)
            .retroBevel(if (sunk) BevelStyle.Pressed else BevelStyle.Raised)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .offset(x = shift, y = shift),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) RetroColors.Text else RetroColors.DisabledText,
        ) { content() }
    }
}

/** [RetroButton] with a text label. */
@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDefault: Boolean = false,
) {
    RetroButton(onClick = onClick, modifier = modifier, enabled = enabled, isDefault = isDefault) {
        Text(text, style = RetroTypography.Body, color = LocalContentColor.current)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFC0C0C0)
@Composable
private fun RetroButtonPreview() {
    RetroTheme {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroButton("OK", onClick = {}, isDefault = true)
            RetroButton("Cancel", onClick = {})
            RetroButton(onClick = {}, latched = true) { Text("Pressed", style = RetroTypography.Body) }
            RetroButton("Disabled", onClick = {}, enabled = false)
        }
    }
}
