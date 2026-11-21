package dev.phonefocus.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colour tokens of the retro 90s-desktop look (#6). Generic palette only; no OS branding.
 *
 * Every text colour must reach a contrast ratio of at least 4.5:1 against the background it is
 * drawn on; the pairs the components use are listed in [textPairs] and checked by a unit test.
 */
object RetroColors {
    /** Desktop background behind windows and desktop icons. */
    val Desktop = Color(0xFF007878)

    /** Window, button and taskbar surface. */
    val Surface = Color(0xFFC0C0C0)

    /** Top/left edge of a raised bevel (bottom/right of a sunken one). */
    val BevelLight = Color(0xFFFFFFFF)

    /** Outer bottom/right edge of a raised bevel (top/left of a sunken one). */
    val BevelDark = Color(0xFF404040)

    /** Inner bottom/right edge of a raised bevel (outer top/left of a sunken one). */
    val ShadowMid = Color(0xFF808080)

    /** Window title bar and selection highlight. */
    val TitleBar = Color(0xFF000080)
    val OnTitleBar = Color(0xFFFFFFFF)

    /** Highlight for the recommended "No short videos" option. */
    val Highlight = Color(0xFFFFFFC0)

    /** Background of text fields, sunken panels and lists. */
    val Field = Color(0xFFFFFFFF)

    /** Default text and outline colour (also the 1px outline of the default button). */
    val Text = Color(0xFF000000)

    /** Text on the teal desktop (desktop icon labels). Black on teal is below 4.5:1. */
    val OnDesktop = Color(0xFFFFFFFF)

    /**
     * Text of disabled controls. The classic embossed mid-grey is below 4.5:1 on [Surface],
     * so disabled text uses the dark bevel grey instead.
     */
    val DisabledText = BevelDark

    /** A text colour and the background it is drawn on. */
    data class TextPair(val name: String, val text: Color, val background: Color)

    /** Every text-on-background combination used by the components. */
    val textPairs: List<TextPair> = listOf(
        TextPair("text on surface", Text, Surface),
        TextPair("disabled text on surface", DisabledText, Surface),
        TextPair("text on field", Text, Field),
        TextPair("disabled text on field", DisabledText, Field),
        TextPair("title and selection on title-bar blue", OnTitleBar, TitleBar),
        TextPair("text on highlight", Text, Highlight),
        TextPair("label on desktop", OnDesktop, Desktop),
    )
}
