package dev.phonefocus.ui.launcher

import dev.phonefocus.domain.ExitDelay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class LauncherLabelsTest {
    @Test
    fun `focus button shows Focus, or the remaining time while exit is pending`() {
        assertEquals("Focus", LauncherLabels.focusButton(null))
        assertEquals("Exit 23:41", LauncherLabels.focusButton(Duration.ofHours(23).plusMinutes(41).plusSeconds(7)))
    }

    @Test
    fun `exit delay label`() {
        assertEquals("Exit delay: 24 hours", LauncherLabels.exitDelay(ExitDelay.ONE_DAY))
        assertEquals("Exit delay: 7 days", LauncherLabels.exitDelay(ExitDelay.SEVEN_DAYS))
    }
}
