package dev.phonefocus.ui.setup

import dev.phonefocus.application.ExitDelayOptions
import dev.phonefocus.domain.ExitDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupLabelsTest {
    @Test
    fun `exit delays read like the mockup`() {
        assertEquals(
            listOf("1 minute", "12 hours", "24 hours", "48 hours", "7 days"),
            ExitDelayOptions.available(debugBuild = true).map(SetupLabels::exitDelay),
        )
    }

    @Test
    fun `the confirmation names the exit delay`() {
        assertTrue("48 hours later" in SetupLabels.confirmation(ExitDelay.TWO_DAYS))
    }
}
