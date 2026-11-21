package dev.phonefocus.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {
    @Test
    fun `describe combines app name and version`() {
        assertEquals("phone-focus 1.2.3", AppVersion.describe("1.2.3"))
    }
}
