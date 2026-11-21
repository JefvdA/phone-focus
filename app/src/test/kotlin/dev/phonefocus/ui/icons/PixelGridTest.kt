package dev.phonefocus.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelGridTest {

    @Test
    fun `merges same-coloured pixels into runs and skips transparent ones`() {
        val grid = PixelGrid(listOf("KK.W", ". KK"))
        assertEquals(4, grid.width)
        assertEquals(2, grid.height)
        assertEquals(
            listOf(
                PixelGrid.Run('K', 0, 0, 2),
                PixelGrid.Run('W', 3, 0, 1),
                PixelGrid.Run('K', 2, 1, 2),
            ),
            grid.runs,
        )
        assertEquals(setOf('K', 'W'), grid.keys)
    }

    @Test
    fun `empty grid has no runs`() {
        val grid = PixelGrid(emptyList())
        assertEquals(0, grid.width)
        assertEquals(emptyList<PixelGrid.Run>(), grid.runs)
    }
}
