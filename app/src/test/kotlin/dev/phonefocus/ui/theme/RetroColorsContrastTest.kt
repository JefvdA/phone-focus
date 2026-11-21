package dev.phonefocus.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroColorsContrastTest {

    @Test
    fun `every text pair used by the components reaches 4_5 to 1`() {
        RetroColors.textPairs.forEach { pair ->
            val ratio = contrastRatio(pair.text, pair.background)
            assertTrue("${pair.name}: contrast %.2f < 4.5".format(ratio), ratio >= 4.5)
        }
    }

    @Test
    fun `contrast ratio matches the WCAG reference values`() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrastRatio(RetroColors.Surface, RetroColors.Surface), 0.0001)
        // Symmetric.
        assertEquals(
            contrastRatio(RetroColors.OnDesktop, RetroColors.Desktop),
            contrastRatio(RetroColors.Desktop, RetroColors.OnDesktop),
            0.0001,
        )
    }

    @Test
    fun `tokens match the issue`() {
        assertEquals(Color(0xFF007878), RetroColors.Desktop)
        assertEquals(Color(0xFFC0C0C0), RetroColors.Surface)
        assertEquals(Color(0xFFFFFFFF), RetroColors.BevelLight)
        assertEquals(Color(0xFF404040), RetroColors.BevelDark)
        assertEquals(Color(0xFF808080), RetroColors.ShadowMid)
        assertEquals(Color(0xFF000080), RetroColors.TitleBar)
        assertEquals(Color(0xFFFFFFFF), RetroColors.OnTitleBar)
        assertEquals(Color(0xFFFFFFC0), RetroColors.Highlight)
    }

    @Test
    fun `black on the teal desktop is too low, which is why labels are white`() {
        assertTrue(contrastRatio(Color.Black, RetroColors.Desktop) < 4.5)
    }
}
