package com.mgafk.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GardenTilesTest {

    private val capacity = GardenTiles.DIRT_TILES_PER_GARDEN

    @Test fun capacity_isTheFullPlot() {
        assertEquals(200, capacity)
    }

    @Test fun defaultCapacity_isTheFullPlot() {
        assertEquals(capacity, GardenTiles.freeTileCount(emptySet()))
        assertEquals(0, GardenTiles.firstFreeTile(emptySet()))
    }

    @Test fun freeTileCount_emptyGardenIsFullyFree() {
        assertEquals(capacity, GardenTiles.freeTileCount(emptySet(), capacity))
    }

    @Test fun freeTileCount_countsTilesAboveTheHighestOccupiedIndex() {
        // The regression: tileObjects only carries occupied tiles, so everything past the
        // highest key used to be invisible.
        val occupied = (0..49).toSet()
        assertEquals(150, GardenTiles.freeTileCount(occupied, capacity))
    }

    @Test fun freeTileCount_harvestingTheHighestTileAddsOneFreeTile() {
        val occupied = (0..17).toSet() + 32
        val before = GardenTiles.freeTileCount(occupied, capacity)
        val after = GardenTiles.freeTileCount(occupied - 32, capacity)
        assertEquals(before + 1, after)
    }

    @Test fun freeTileCount_fullGardenHasNoFreeTile() {
        assertEquals(0, GardenTiles.freeTileCount((0 until capacity).toSet(), capacity))
    }

    @Test fun freeTileCount_ignoresOutOfRangeKeys() {
        assertEquals(capacity - 1, GardenTiles.freeTileCount(setOf(0, -3, capacity + 10), capacity))
    }

    @Test fun firstFreeTile_emptyGardenStartsAtZero() {
        assertEquals(0, GardenTiles.firstFreeTile(emptySet(), capacity))
    }

    @Test fun firstFreeTile_fillsGapsBeforeExtendingPastTheHighestTile() {
        assertEquals(7, GardenTiles.firstFreeTile((0..19).toSet() - 7, capacity))
    }

    @Test fun firstFreeTile_goesPastTheHighestOccupiedTile() {
        assertEquals(50, GardenTiles.firstFreeTile((0..49).toSet(), capacity))
    }

    @Test fun firstFreeTile_fullGardenHasNone() {
        assertNull(GardenTiles.firstFreeTile((0 until capacity).toSet(), capacity))
    }
}
