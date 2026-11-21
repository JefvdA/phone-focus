package dev.phonefocus.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.phonefocus.ui.theme.RetroColors

/**
 * Pixel icons of the retro design system, drawn on a pixel grid and stored as vectors so they
 * stay crisp at any size. All original, generic artwork (no OS logos).
 *
 * Colour keys: `K` black, `W` white, `G` surface grey, `M` mid grey, `D` dark grey, `N` navy,
 * `T` teal, `Y` pale yellow. Monochrome icons ([Close], [Check], [ArrowDown]) use only `K` and can
 * be tinted with a colour filter.
 */
object RetroIcons {
    private val palette = mapOf(
        'K' to Color.Black,
        'W' to RetroColors.BevelLight,
        'G' to RetroColors.Surface,
        'M' to RetroColors.ShadowMid,
        'D' to RetroColors.BevelDark,
        'N' to RetroColors.TitleBar,
        'T' to RetroColors.Desktop,
        'Y' to RetroColors.Highlight,
    )

    /** Window close cross. */
    val Close: ImageVector = pixelIcon(
        "Close",
        "KK....KK",
        ".KK..KK.",
        "..KKKK..",
        "...KK...",
        "..KKKK..",
        ".KK..KK.",
        "KK....KK",
    )

    /** Check mark for check boxes. */
    val Check: ImageVector = pixelIcon(
        "Check",
        "......K",
        ".....KK",
        "K...KKK",
        "KK.KKK.",
        "KKKKK..",
        ".KKK...",
        "..K....",
    )

    /** Small down arrow for drop-downs. */
    val ArrowDown: ImageVector = pixelIcon(
        "ArrowDown",
        "KKKKKKK",
        ".KKKKK.",
        "..KKK..",
        "...K...",
    )

    /** Padlock: focus mode / locked. */
    val Lock: ImageVector = pixelIcon(
        "Lock",
        "................",
        ".....KKKKKK.....",
        "....KMMMMMMK....",
        "...KMK....KMK...",
        "...KMK....KMK...",
        "...KMK....KMK...",
        "...KMK....KMK...",
        "..KKKKKKKKKKKK..",
        "..KYYYYYYYYYYK..",
        "..KYWWWWWWWWDK..",
        "..KYWWWKKWWWDK..",
        "..KYWWWKKWWWDK..",
        "..KYWWWWKWWWDK..",
        "..KYWWWWWWWWDK..",
        "..KDDDDDDDDDDK..",
        "..KKKKKKKKKKKK..",
    )

    /** Menu: a list with bullet rows. */
    val Menu: ImageVector = pixelIcon(
        "Menu",
        "................",
        ".KKKKKKKKKKKKKK.",
        ".KWWWWWWWWWWWMK.",
        ".KWNNWKKKKKKWMK.",
        ".KWNNWWWWWWWWMK.",
        ".KWWWWWWWWWWWMK.",
        ".KWNNWKKKKKKWMK.",
        ".KWNNWWWWWWWWMK.",
        ".KWWWWWWWWWWWMK.",
        ".KWNNWKKKKKKWMK.",
        ".KWNNWWWWWWWWMK.",
        ".KWWWWWWWWWWWMK.",
        ".KMMMMMMMMMMMMK.",
        ".KKKKKKKKKKKKKK.",
        "................",
        "................",
    )

    /** Generic app placeholder: a tiny window. */
    val AppPlaceholder: ImageVector = pixelIcon(
        "AppPlaceholder",
        "................",
        "KKKKKKKKKKKKKKKK",
        "KNNNNNNNNNNWNWNK",
        "KNNNNNNNNNNNNNNK",
        "KGGGGGGGGGGGGGGK",
        "KGWWWWWWWWWWWWMK",
        "KGWTTTTWWWWWWWMK",
        "KGWTTTTWKKKKWWMK",
        "KGWTTTTWWWWWWWMK",
        "KGWWWWWWWKKKWWMK",
        "KGWKKKKWWWWWWWMK",
        "KGWWWWWWWKKKKWMK",
        "KGWMMMMMMMMMMMMK",
        "KGGGGGGGGGGGGGGK",
        "KKKKKKKKKKKKKKKK",
        "................",
    )

    /** All icons, for previews. */
    val all: List<ImageVector> get() = listOf(Close, Check, ArrowDown, Lock, Menu, AppPlaceholder)

    private fun pixelIcon(name: String, vararg rows: String): ImageVector {
        val grid = PixelGrid(rows.toList())
        val builder = ImageVector.Builder(
            name = "Retro.$name",
            defaultWidth = (grid.width * 2).dp,
            defaultHeight = (grid.height * 2).dp,
            viewportWidth = grid.width.toFloat(),
            viewportHeight = grid.height.toFloat(),
        )
        for (key in grid.keys) {
            val color = requireNotNull(palette[key]) { "Unknown colour key '$key' in icon $name" }
            builder.path(fill = SolidColor(color)) {
                grid.runs.filter { it.key == key }.forEach { pixelRun(it) }
            }
        }
        return builder.build()
    }

    private fun PathBuilder.pixelRun(run: PixelGrid.Run) {
        moveTo(run.x.toFloat(), run.y.toFloat())
        horizontalLineToRelative(run.length.toFloat())
        verticalLineToRelative(1f)
        horizontalLineToRelative(-run.length.toFloat())
        close()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFC0C0C0)
@Composable
private fun RetroIconsPreview() {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RetroIcons.all.forEach { Image(it, contentDescription = null, modifier = Modifier.size(32.dp)) }
    }
}
