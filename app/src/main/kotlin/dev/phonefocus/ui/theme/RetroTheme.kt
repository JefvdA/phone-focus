package dev.phonefocus.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle

private val RetroColorScheme = lightColorScheme(
    primary = RetroColors.TitleBar,
    onPrimary = RetroColors.OnTitleBar,
    secondary = RetroColors.Desktop,
    onSecondary = RetroColors.OnDesktop,
    background = RetroColors.Surface,
    onBackground = RetroColors.Text,
    surface = RetroColors.Surface,
    onSurface = RetroColors.Text,
    surfaceVariant = RetroColors.Field,
    onSurfaceVariant = RetroColors.Text,
    outline = RetroColors.BevelDark,
)

private fun TextStyle.pixelify() = copy(fontFamily = PixelifySans)

private val RetroMaterialTypography = Typography().run {
    copy(
        displayLarge = displayLarge.pixelify(),
        displayMedium = displayMedium.pixelify(),
        displaySmall = displaySmall.pixelify(),
        headlineLarge = headlineLarge.pixelify(),
        headlineMedium = headlineMedium.pixelify(),
        headlineSmall = headlineSmall.pixelify(),
        titleLarge = titleLarge.pixelify(),
        titleMedium = titleMedium.pixelify(),
        titleSmall = titleSmall.pixelify(),
        bodyLarge = bodyLarge.pixelify(),
        bodyMedium = bodyMedium.pixelify(),
        bodySmall = bodySmall.pixelify(),
        labelLarge = labelLarge.pixelify(),
        labelMedium = labelMedium.pixelify(),
        labelSmall = labelSmall.pixelify(),
    )
}

/**
 * Theme for every screen of the app: retro tokens plus a Material theme mapped onto them, so
 * Material widgets that are still in use (e.g. the debug panel) pick up the font and colours.
 */
@Composable
fun RetroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RetroColorScheme, typography = RetroMaterialTypography) {
        CompositionLocalProvider(LocalContentColor provides RetroColors.Text, content = content)
    }
}
