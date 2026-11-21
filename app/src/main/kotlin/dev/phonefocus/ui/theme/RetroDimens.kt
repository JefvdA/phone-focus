package dev.phonefocus.ui.theme

import androidx.compose.ui.unit.dp

/** Size tokens of the retro design system (#6). */
object RetroDimens {
    /** Minimum size of anything that can be tapped. */
    val MinTouchTarget = 44.dp

    /** Thickness of one bevel line ("one pixel" of the 90s look). */
    val BevelLine = 2.dp

    /** A full bevel: outer plus inner line. */
    val Bevel = BevelLine * 2

    /** Outline around the default button. */
    val DefaultButtonOutline = 1.dp

    val ButtonMinWidth = 88.dp
    val ButtonMinHeight = MinTouchTarget
    val FieldMinHeight = MinTouchTarget
    val ToggleRowMinHeight = MinTouchTarget
    val DropdownItemMinHeight = MinTouchTarget
    val TitleBarMinHeight = MinTouchTarget
    val CloseButtonTouchTarget = MinTouchTarget
    val TaskbarMinHeight = MinTouchTarget + 8.dp
    val DesktopIconMinWidth = 76.dp
    val DesktopIconMinHeight = 76.dp

    /** Visual box of a checkbox or radio; the whole labelled row is the touch target. */
    val ToggleBox = 20.dp

    val ProgressBarHeight = 24.dp
    val ProgressSegmentWidth = 10.dp
    val ProgressSegmentGap = 2.dp

    val ContentPadding = 8.dp
}
