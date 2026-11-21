package dev.phonefocus.application

import dev.phonefocus.domain.ExitDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class ExitDelayOptionsTest {
    @Test
    fun `release builds offer only the presets`() {
        assertEquals(ExitDelay.PRESETS, ExitDelayOptions.available(debugBuild = false))
    }

    @Test
    fun `debug builds offer the one-minute delay first`() {
        assertEquals(listOf(ExitDelayOptions.DEBUG_ONE_MINUTE) + ExitDelay.PRESETS, ExitDelayOptions.available(debugBuild = true))
        assertEquals(Duration.ofMinutes(1), ExitDelayOptions.DEBUG_ONE_MINUTE.duration)
    }

    @Test
    fun `when tightening, shorter delays than the current one are disabled and the current one is enabled`() {
        val choices = ExitDelayOptions.forTightening(ExitDelay.ONE_DAY, ExitDelayOptions.available(debugBuild = true))
        assertEquals(
            listOf(
                ExitDelayChoice(ExitDelayOptions.DEBUG_ONE_MINUTE, enabled = false),
                ExitDelayChoice(ExitDelay.TWELVE_HOURS, enabled = false),
                ExitDelayChoice(ExitDelay.ONE_DAY, enabled = true),
                ExitDelayChoice(ExitDelay.TWO_DAYS, enabled = true),
                ExitDelayChoice(ExitDelay.SEVEN_DAYS, enabled = true),
            ),
            choices,
        )
    }

    @Test
    fun `when tightening, a current delay that is not offered is still shown, in order`() {
        // E.g. focus started with the one-minute debug delay, now running a release build.
        val choices = ExitDelayOptions.forTightening(ExitDelayOptions.DEBUG_ONE_MINUTE, ExitDelay.PRESETS)
        assertEquals(listOf(ExitDelayOptions.DEBUG_ONE_MINUTE) + ExitDelay.PRESETS, choices.map { it.delay })
        assertTrue(choices.all { it.enabled })
    }

    @Test
    fun `when tightening with the longest delay, only that one is enabled`() {
        val choices = ExitDelayOptions.forTightening(ExitDelay.SEVEN_DAYS, ExitDelay.PRESETS)
        assertEquals(listOf(ExitDelay.SEVEN_DAYS), choices.filter { it.enabled }.map { it.delay })
    }
}
