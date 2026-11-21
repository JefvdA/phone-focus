package dev.phonefocus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme

/**
 * 90s radio button: sunken white circle with a black dot when [selected], followed by
 * [label]. Put a group of them in a container with `Modifier.selectableGroup()`.
 */
@Composable
fun RetroRadio(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlight: Boolean = false,
) {
    RetroToggleRow(
        label = label,
        enabled = enabled,
        highlight = highlight,
        modifier = modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Canvas(Modifier.size(RetroDimens.ToggleBox)) {
            val line = RetroDimens.BevelLine.toPx()
            // Outer ring: mid grey top-left, white bottom-right.
            drawArc(RetroColors.ShadowMid, 135f, 180f, useCenter = true)
            drawArc(RetroColors.BevelLight, -45f, 180f, useCenter = true)
            // Inner ring: dark grey top-left, surface bottom-right.
            val inner = Size(size.width - 2 * line, size.height - 2 * line)
            drawArc(RetroColors.BevelDark, 135f, 180f, useCenter = true, topLeft = Offset(line, line), size = inner)
            drawArc(RetroColors.Surface, -45f, 180f, useCenter = true, topLeft = Offset(line, line), size = inner)
            drawCircle(if (enabled) RetroColors.Field else RetroColors.Surface, radius = size.minDimension / 2 - 2 * line)
            if (selected) {
                drawCircle(
                    if (enabled) RetroColors.Text else RetroColors.DisabledText,
                    radius = size.minDimension / 5,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFC0C0C0)
@Composable
private fun RetroRadioPreview() {
    RetroTheme {
        Column(Modifier.padding(8.dp).selectableGroup()) {
            var choice by remember { mutableIntStateOf(0) }
            listOf("No short videos", "Allow the app", "Block the app").forEachIndexed { i, label ->
                RetroRadio(choice == i, { choice = i }, label, highlight = i == 0)
            }
            RetroRadio(true, {}, "Disabled", enabled = false)
        }
    }
}
