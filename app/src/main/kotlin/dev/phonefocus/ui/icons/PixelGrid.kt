package dev.phonefocus.ui.icons

/**
 * A pixel-art bitmap written as text rows, one character per pixel. `.` (or space) is
 * transparent; any other character is a colour key. Used to build crisp vector icons.
 */
class PixelGrid(rows: List<String>) {
    val width: Int = rows.maxOfOrNull { it.length } ?: 0
    val height: Int = rows.size

    /** Horizontal run of same-coloured pixels starting at ([x], [y]). */
    data class Run(val key: Char, val x: Int, val y: Int, val length: Int)

    /** All non-transparent pixels, merged into horizontal runs, row by row. */
    val runs: List<Run> = buildList {
        rows.forEachIndexed { y, row ->
            var x = 0
            while (x < row.length) {
                val key = row[x]
                var end = x + 1
                while (end < row.length && row[end] == key) end++
                if (!isTransparent(key)) add(Run(key, x, y, end - x))
                x = end
            }
        }
    }

    /** Colour keys used in the grid. */
    val keys: Set<Char> get() = runs.mapTo(linkedSetOf()) { it.key }

    companion object {
        fun isTransparent(key: Char) = key == '.' || key == ' '
    }
}
