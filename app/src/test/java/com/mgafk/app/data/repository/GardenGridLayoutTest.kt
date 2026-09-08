package com.mgafk.app.data.repository

import com.mgafk.app.data.model.GardenTileRef
import com.mgafk.app.data.model.GardenTileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A garden is one 23x12 block: a boardwalk ring around two 10x10 fields of dirt, split by a
 * boardwalk column down the middle. Crops only ever go on the dirt, but a crystal can be
 * planted on either surface, so the grid has to name both.
 *
 * The expectations below were read off the game's own map atom, which numbers the dirt tiles
 * row by row across both fields and the boardwalk tiles row by row around them. All six player
 * slots share this layout.
 */
class GardenGridLayoutTest {

    private fun refAt(col: Int, row: Int) = GardenGridLayout.tileAt(col, row)

    private fun dirt(index: Int) = GardenTileRef(GardenTileType.Dirt, index)

    private fun boardwalk(index: Int) = GardenTileRef(GardenTileType.Boardwalk, index)

    @Test fun `the top row is all boardwalk, numbered left to right`() {
        assertEquals(boardwalk(0), refAt(0, 0))
        assertEquals(boardwalk(11), refAt(11, 0))
        assertEquals(boardwalk(22), refAt(22, 0))
    }

    @Test fun `the dirt starts inside the ring`() {
        assertEquals(dirt(0), refAt(1, 1))
        assertEquals(dirt(9), refAt(10, 1))
    }

    /** The middle boardwalk column splits the dirt without interrupting its numbering. */
    @Test fun `the two dirt fields are numbered as one row`() {
        assertEquals(boardwalk(24), refAt(11, 1))
        assertEquals(dirt(10), refAt(12, 1))
        assertEquals(dirt(19), refAt(21, 1))
    }

    @Test fun `dirt numbering continues on the next row`() {
        assertEquals(dirt(20), refAt(1, 2))
        assertEquals(dirt(199), refAt(21, 10))
    }

    /** Each middle row contributes three boardwalk tiles: left edge, middle, right edge. */
    @Test fun `the side edges are boardwalk`() {
        assertEquals(boardwalk(23), refAt(0, 1))
        assertEquals(boardwalk(25), refAt(22, 1))
        assertEquals(boardwalk(26), refAt(0, 2))
    }

    /**
     * Taken from a real capture: two Rain Ward crystals sat side by side at boardwalk 60 and
     * 61, which is the bottom edge.
     */
    @Test fun `the bottom row continues the boardwalk numbering`() {
        assertEquals(boardwalk(53), refAt(0, 11))
        assertEquals(boardwalk(60), refAt(7, 11))
        assertEquals(boardwalk(61), refAt(8, 11))
        assertEquals(boardwalk(75), refAt(22, 11))
    }

    @Test fun `cells outside the block have no tile`() {
        assertNull(refAt(-1, 0))
        assertNull(refAt(0, -1))
        assertNull(refAt(GardenGridLayout.COLS, 0))
        assertNull(refAt(0, GardenGridLayout.ROWS))
    }

    /**
     * The strongest statement available: the grid covers every tile of both maps exactly once
     * and invents none. A mapping that is merely plausible cell by cell still fails this.
     */
    @Test fun `the grid covers both maps exactly once`() {
        val refs = buildList {
            for (row in 0 until GardenGridLayout.ROWS) {
                for (col in 0 until GardenGridLayout.COLS) {
                    add(refAt(col, row))
                }
            }
        }
        assertEquals(GardenGridLayout.COLS * GardenGridLayout.ROWS, refs.size)
        assertEquals("every cell belongs to a map", 0, refs.count { it == null })

        val dirtIndices = refs.filter { it?.tileType == GardenTileType.Dirt }.map { it!!.index }
        val boardwalkIndices = refs.filter { it?.tileType == GardenTileType.Boardwalk }.map { it!!.index }
        assertEquals((0 until GardenGridLayout.DIRT_TILES).toList(), dirtIndices.sorted())
        assertEquals((0 until GardenGridLayout.BOARDWALK_TILES).toList(), boardwalkIndices.sorted())
    }
}
