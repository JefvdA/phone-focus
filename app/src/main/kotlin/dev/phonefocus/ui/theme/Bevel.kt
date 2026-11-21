package dev.phonefocus.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/** The two-line 3D edge of 90s controls. */
enum class BevelStyle {
    /** Windows, buttons, taskbar: light top/left, dark bottom/right. */
    Raised,

    /** Pressed buttons: the raised bevel inverted. */
    Pressed,

    /** Text fields, panels, check boxes: looks cut into the surface. */
    Sunken,
}

/**
 * Fills the element with [background], draws a [style] bevel on its edge and insets the content
 * by the bevel thickness ([RetroDimens.Bevel]).
 */
fun Modifier.retroBevel(style: BevelStyle, background: Color = RetroColors.Surface): Modifier =
    drawBehind {
        drawRect(background)
        val line = RetroDimens.BevelLine.toPx()
        when (style) {
            BevelStyle.Raised -> {
                bevelEdge(RetroColors.BevelLight, RetroColors.BevelDark, inset = 0f, line = line)
                bevelEdge(background, RetroColors.ShadowMid, inset = line, line = line)
            }
            BevelStyle.Pressed -> {
                bevelEdge(RetroColors.BevelDark, RetroColors.BevelLight, inset = 0f, line = line)
                bevelEdge(RetroColors.ShadowMid, background, inset = line, line = line)
            }
            BevelStyle.Sunken -> {
                bevelEdge(RetroColors.ShadowMid, RetroColors.BevelLight, inset = 0f, line = line)
                bevelEdge(RetroColors.BevelDark, RetroColors.Surface, inset = line, line = line)
            }
        }
    }.padding(RetroDimens.Bevel)

/** One rectangular edge line: [topLeft] on top and left, [bottomRight] on bottom and right. */
private fun DrawScope.bevelEdge(topLeft: Color, bottomRight: Color, inset: Float, line: Float) {
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    if (right - left < 2 * line || bottom - top < 2 * line) return
    drawRect(topLeft, Offset(left, top), Size(right - left - line, line))
    drawRect(topLeft, Offset(left, top), Size(line, bottom - top - line))
    drawRect(bottomRight, Offset(left, bottom - line), Size(right - left, line))
    drawRect(bottomRight, Offset(right - line, top), Size(line, bottom - top))
}
