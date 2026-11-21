package dev.phonefocus.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.phonefocus.R

/**
 * Pixelify Sans (SIL Open Font License 1.1, bundled in `res/font`, licence text in
 * `assets/licenses`). It is a variable font; the weight axis is set from [FontWeight].
 * Glyphs it lacks fall back to the system sans-serif font.
 */
val PixelifySans = FontFamily(
    Font(R.font.pixelify_sans, FontWeight.Normal),
    Font(R.font.pixelify_sans, FontWeight.Medium),
    Font(R.font.pixelify_sans, FontWeight.Bold),
)

/** Text styles of the retro design system. */
object RetroTypography {
    val Title = TextStyle(fontFamily = PixelifySans, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)
    val Body = TextStyle(fontFamily = PixelifySans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 20.sp)
    val Label = TextStyle(fontFamily = PixelifySans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp)
}
