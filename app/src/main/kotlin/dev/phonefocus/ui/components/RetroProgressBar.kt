package dev.phonefocus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.phonefocus.ui.theme.RetroColors
import dev.phonefocus.ui.theme.RetroDimens
import dev.phonefocus.ui.theme.RetroTheme
import kotlin.math.floor

/** Number of fully drawn segments for [progress] (0..1) out of [total]. */
internal fun filledSegments(progress: Float, total: Int): Int =
    floor(progress.coerceIn(0f, 1f) * total + 1e-4f).toInt().coerceIn(0, total)

/** 90s block progress bar: navy segments in a sunken white trough. [progress] is 0..1. */
@Composable
fun RetroProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val clamped = progress.coerceIn(0f, 1f)
    RetroSunkenPanel(
        modifier = modifier
            .height(RetroDimens.ProgressBarHeight)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) },
    ) {
        Canvas(Modifier.fillMaxSize().padding(RetroDimens.ProgressSegmentGap)) {
            val segment = RetroDimens.ProgressSegmentWidth.toPx()
            val gap = RetroDimens.ProgressSegmentGap.toPx()
            val total = ((size.width + gap) / (segment + gap)).toInt().coerceAtLeast(1)
            repeat(filledSegments(clamped, total)) { i ->
                drawRect(RetroColors.TitleBar, Offset(i * (segment + gap), 0f), Size(segment, size.height))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFC0C0C0, widthDp = 240)
@Composable
private fun RetroProgressBarPreview() {
    RetroTheme {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0f, 0.35f, 0.7f, 1f).forEach { RetroProgressBar(it, Modifier.fillMaxWidth()) }
        }
    }
}
