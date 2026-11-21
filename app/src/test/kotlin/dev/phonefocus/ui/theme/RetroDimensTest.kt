package dev.phonefocus.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroDimensTest {

    @Test
    fun `every tappable size is at least 44dp`() {
        val targets = mapOf(
            "MinTouchTarget" to RetroDimens.MinTouchTarget,
            "ButtonMinHeight" to RetroDimens.ButtonMinHeight,
            "FieldMinHeight" to RetroDimens.FieldMinHeight,
            "ToggleRowMinHeight" to RetroDimens.ToggleRowMinHeight,
            "DropdownItemMinHeight" to RetroDimens.DropdownItemMinHeight,
            "CloseButtonTouchTarget" to RetroDimens.CloseButtonTouchTarget,
            "TaskbarMinHeight" to RetroDimens.TaskbarMinHeight,
            "DesktopIconMinWidth" to RetroDimens.DesktopIconMinWidth,
            "DesktopIconMinHeight" to RetroDimens.DesktopIconMinHeight,
        )
        targets.forEach { (name, size) -> assertTrue("$name is $size", size >= 44.dp) }
    }

    @Test
    fun `taskbar fits a full-height button`() {
        assertTrue(RetroDimens.TaskbarMinHeight - RetroDimens.Bevel >= RetroDimens.ButtonMinHeight)
    }
}
