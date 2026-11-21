package dev.phonefocus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTypography

/**
 * Shared layout of check box and radio rows: the [indicator] followed by the [label]. The whole
 * row (at least [RetroDimens.ToggleRowMinHeight] tall) is the touch target, passed in through
 * [modifier]. [highlight] puts the label on pale yellow (used for "No short videos").
 */
@Composable
internal fun RetroToggleRow(
    label: String,
    enabled: Boolean,
    highlight: Boolean,
    modifier: Modifier,
    indicator: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.defaultMinSize(minHeight = RetroDimens.ToggleRowMinHeight).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        indicator()
        Text(
            text = label,
            style = RetroTypography.Body,
            color = if (enabled) RetroColors.Text else RetroColors.DisabledText,
            modifier = (if (highlight) Modifier.background(RetroColors.Highlight) else Modifier)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
