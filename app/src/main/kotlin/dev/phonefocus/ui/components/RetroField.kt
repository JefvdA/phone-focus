package dev.phonefocus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.phonefocus.ui.theme.BevelStyle
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme
import dev.phonefocus.ui.theme.RetroTypography
import dev.phonefocus.ui.theme.retroBevel

/** Sunken panel (inverted bevel), e.g. for lists, read-only values or a status area. */
@Composable
fun RetroSunkenPanel(
    modifier: Modifier = Modifier,
    background: Color = RetroColors.Field,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.retroBevel(BevelStyle.Sunken, background), content = content)
}

/** Single-line 90s text field: white, sunken, at least [RetroDimens.MinTouchTarget] tall. */
@Composable
fun RetroField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    val textColor = if (enabled) RetroColors.Text else RetroColors.DisabledText
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = RetroTypography.Body.copy(color = textColor),
        cursorBrush = SolidColor(RetroColors.Text),
        modifier = modifier.defaultMinSize(minHeight = RetroDimens.FieldMinHeight),
        decorationBox = { inner ->
            RetroSunkenPanel(
                modifier = Modifier.defaultMinSize(minHeight = RetroDimens.FieldMinHeight),
                background = if (enabled) RetroColors.Field else RetroColors.Surface,
            ) {
                Box(
                    Modifier.align(Alignment.CenterStart).padding(horizontal = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = RetroTypography.Body, color = RetroColors.DisabledText)
                    }
                    inner()
                }
            }
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFC0C0C0)
@Composable
private fun RetroFieldPreview() {
    RetroTheme {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var text by remember { mutableStateOf("Hello") }
            RetroField(text, { text = it }, Modifier.fillMaxWidth())
            RetroField("", {}, Modifier.fillMaxWidth(), placeholder = "Search apps")
            RetroField("Disabled", {}, Modifier.fillMaxWidth(), enabled = false)
            RetroSunkenPanel(Modifier.fillMaxWidth()) {
                Text("Sunken panel", Modifier.padding(8.dp), style = RetroTypography.Body)
            }
        }
    }
}
