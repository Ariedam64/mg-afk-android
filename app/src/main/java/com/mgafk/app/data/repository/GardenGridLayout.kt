package com.mgafk.app.data.repository

import com.mgafk.app.data.model.GardenTileRef
import com.mgafk.app.data.model.GardenTileType

/**
 * Where each cell of a garden sits in the game's two tile maps.
 *
 * A garden is one 23x12 block: a boardwalk ring around two 10x10 fields of dirt, separated by
 * a boardwalk column down the middle. The game numbers each map row by row, independently, and
 * every player slot uses the same layout, so a cell's map and index follow from its position
 * alone. Crops go on the dirt; a crystal can go on either surface, which is why this exists.
 *
 * Read off the game's map atom and checked against it for all 276 cells.
 */
object GardenGridLayout {

    const val COLS = 23
    const val ROWS = 12
    const val DIRT_TILES = 200
    const val BOARDWALK_TILES = 76

    /** Dirt is 20 tiles wide across both fields, numbered as a single row. */
    private const val DIRT_COLS = 20

    /** The boardwalk column that splits the two dirt fields. */
    private const val MIDDLE_COL = 11

    /** Left edge, middle column, right edge: what a row between the top and bottom contributes. */
    private const val BOARDWALK_PER_MIDDLE_ROW = 3

    private const val FIRST_MIDDLE_ROW_BOARDWALK = COLS
    private const val BOTTOM_ROW_FIRST_BOARDWALK =
        FIRST_MIDDLE_ROW_BOARDWALK + (ROWS - 2) * BOARDWALK_PER_MIDDLE_ROW

    fun tileAt(col: Int, row: Int): GardenTileRef? {
        if (col !in 0 until COLS || row !in 0 until ROWS) return null

        if (row == 0) return boardwalk(col)
        if (row == ROWS - 1) return boardwalk(BOTTOM_ROW_FIRST_BOARDWALK + col)

        val rowFirst = FIRST_MIDDLE_ROW_BOARDWALK + (row - 1) * BOARDWALK_PER_MIDDLE_ROW
        when (col) {
            0 -> return boardwalk(rowFirst)
            MIDDLE_COL -> return boardwalk(rowFirst + 1)
            COLS - 1 -> return boardwalk(rowFirst + 2)
        }

        // Dirt: drop the left edge, and the middle column for the right-hand field.
        val dirtCol = if (col < MIDDLE_COL) col - 1 else col - 2
        return GardenTileRef(GardenTileType.Dirt, (row - 1) * DIRT_COLS + dirtCol)
    }

    private fun boardwalk(index: Int) = GardenTileRef(GardenTileType.Boardwalk, index)
}
