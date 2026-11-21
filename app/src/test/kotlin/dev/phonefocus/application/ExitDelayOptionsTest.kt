package dev.phonefocus.application

import dev.phonefocus.domain.ExitDelay
import org.junit.Assert.assertEquals
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
}
