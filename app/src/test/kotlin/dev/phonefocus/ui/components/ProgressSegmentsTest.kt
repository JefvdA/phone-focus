package dev.phonefocus.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressSegmentsTest {

    @Test
    fun `fills whole segments and clamps out-of-range progress`() {
        assertEquals(0, filledSegments(0f, 10))
        assertEquals(3, filledSegments(0.35f, 10))
        assertEquals(7, filledSegments(0.7f, 10))
        assertEquals(10, filledSegments(1f, 10))
        assertEquals(0, filledSegments(-1f, 10))
        assertEquals(10, filledSegments(2f, 10))
    }
}
