package dev.phonefocus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.phonefocus.ui.icons.RetroIcons
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme

/** 90s check box: sunken white box with a pixel check mark, followed by [label]. */
@Composable
fun RetroCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlight: Boolean = false,
) {
    RetroToggleRow(
        label = label,
        enabled = enabled,
        highlight = highlight,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
    ) {
        RetroSunkenPanel(
            modifier = Modifier.size(RetroDimens.ToggleBox),
            background = if (enabled) RetroColors.Field else RetroColors.Surface,
        ) {
            if (checked) {
                Image(
                    imageVector = RetroIcons.Check,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(if (enabled) RetroColors.Text else RetroColors.DisabledText),
                    modifier = Modifier.align(Alignment.Center).size(10.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFC0C0C0)
@Composable
private fun RetroCheckboxPreview() {
    RetroTheme {
        Column(Modifier.padding(8.dp)) {
            var checked by remember { mutableStateOf(true) }
            RetroCheckbox(checked, { checked = it }, "Block notifications")
            RetroCheckbox(false, {}, "Unchecked")
            RetroCheckbox(true, {}, "No short videos", highlight = true)
            RetroCheckbox(true, {}, "Disabled", enabled = false)
        }
    }
}
