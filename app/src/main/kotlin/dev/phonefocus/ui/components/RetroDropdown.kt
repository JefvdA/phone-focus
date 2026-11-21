package dev.phonefocus.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.phonefocus.ui.icons.RetroIcons
import dev.phonefocus.ui.theme.BevelStyle
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme
import dev.phonefocus.ui.theme.RetroTypography
import dev.phonefocus.ui.theme.retroBevel

/**
 * 90s drop-down list: a sunken field showing the [selected] option with a raised arrow button.
 * Tapping opens a list of [options] right below it; the selected option is shown in navy.
 * Options for which [isHighlighted] is true (e.g. "No short videos") are drawn on pale yellow,
 * in the list and in the field while selected.
 */
@Composable
fun <T> RetroDropdown(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionLabel: (T) -> String = { it.toString() },
    isHighlighted: (T) -> Boolean = { false },
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var fieldWidth by remember { mutableIntStateOf(0) }
    var fieldHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(modifier) {
        RetroSunkenPanel(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = RetroDimens.FieldMinHeight)
                .onSizeChanged { fieldWidth = it.width; fieldHeight = it.height }
                .clickable(enabled = enabled, role = Role.DropdownList) { expanded = true },
            background = when {
                !enabled -> RetroColors.Surface
                isHighlighted(selected) -> RetroColors.Highlight
                else -> RetroColors.Field
            },
        ) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = optionLabel(selected),
                    style = RetroTypography.Body,
                    color = if (enabled) RetroColors.Text else RetroColors.DisabledText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                )
                Box(
                    Modifier.width(32.dp).height(RetroDimens.FieldMinHeight - RetroDimens.Bevel * 2)
                        .retroBevel(if (expanded) BevelStyle.Pressed else BevelStyle.Raised),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        RetroIcons.ArrowDown,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(if (enabled) RetroColors.Text else RetroColors.DisabledText),
                        modifier = Modifier.size(14.dp, 8.dp),
                    )
                }
            }
        }
        if (expanded) {
            Popup(
                offset = IntOffset(0, fieldHeight),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                RetroDropdownList(
                    options = options,
                    selected = selected,
                    optionLabel = optionLabel,
                    isHighlighted = isHighlighted,
                    onSelect = { expanded = false; onSelect(it) },
                    modifier = Modifier.width(with(density) { fieldWidth.toDp() }),
                )
            }
        }
    }
}

/** The opened list of a [RetroDropdown]: black outline, white items of touch-target height. */
@Composable
internal fun <T> RetroDropdownList(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: (T) -> Boolean = { false },
) {
    Column(
        modifier
            .border(1.dp, RetroColors.Text, RectangleShape)
            .background(RetroColors.Field)
            .padding(1.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = RetroDimens.DropdownItemMinHeight)
                    .background(
                        when {
                            isSelected -> RetroColors.TitleBar
                            isHighlighted(option) -> RetroColors.Highlight
                            else -> RetroColors.Field
                        },
                    )
                    .semantics { this.selected = isSelected }
                    .clickable { onSelect(option) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    optionLabel(option),
                    style = RetroTypography.Body,
                    color = if (isSelected) RetroColors.OnTitleBar else RetroColors.Text,
                )
            }
        }
    }
}

private val previewOptions = listOf("1 minute", "5 minutes", "15 minutes", "1 hour")

@Preview(showBackground = true, backgroundColor = 0xFFC0C0C0, widthDp = 240)
@Composable
private fun RetroDropdownPreview() {
    RetroTheme {
        Column(Modifier.padding(8.dp).widthIn(max = 240.dp)) {
            var choice by remember { mutableStateOf(previewOptions[1]) }
            RetroDropdown(previewOptions, choice, { choice = it })
            Box(Modifier.height(8.dp))
            RetroDropdown(previewOptions, previewOptions[0], {}, enabled = false)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFC0C0C0, widthDp = 240)
@Composable
private fun RetroDropdownListPreview() {
    RetroTheme {
        RetroDropdownList(previewOptions, previewOptions[1], { it }, {}, Modifier.padding(8.dp))
    }
}
