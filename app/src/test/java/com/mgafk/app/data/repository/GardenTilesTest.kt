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

    // ── Manual placement: which tiles a given seed or pot may go on ──

    private val smallPlot = 6

    private fun plantable(
        species: String?,
        speciesByTile: Map<Int, String> = emptyMap(),
        plantCountByTile: Map<Int, Int> = emptyMap(),
        blockedTileIds: Set<Int> = emptySet(),
        maxGrowSlots: Int = 1,
    ) = GardenTiles.plantableTiles(
        species = species,
        speciesByTile = speciesByTile,
        plantCountByTile = plantCountByTile,
        blockedTileIds = blockedTileIds,
        maxGrowSlots = maxGrowSlots,
        capacity = smallPlot,
    )

    @Test fun plantableTiles_emptyGardenOffersEveryTile() {
        assertEquals((0 until smallPlot).toSet(), plantable(species = "Carrot"))
    }

    @Test fun plantableTiles_skipsTilesHoldingAnotherSpecies() {
        assertEquals(
            setOf(0, 1, 3, 5),
            plantable(
                species = "Carrot",
                speciesByTile = mapOf(2 to "Tomato", 4 to "Tomato"),
                plantCountByTile = mapOf(2 to 1, 4 to 1),
            ),
        )
    }

    /** Same rule auto-plant uses: a tile of the same species keeps taking seeds until it is full. */
    @Test fun plantableTiles_keepsTilesOfTheSameSpeciesUntilTheyAreFull() {
        val speciesByTile = mapOf(1 to "Carrot", 2 to "Carrot")
        assertEquals(
            setOf(0, 1, 3, 4, 5),
            plantable(
                species = "Carrot",
                speciesByTile = speciesByTile,
                // Tile 1 has room for more, tile 2 is at capacity.
                plantCountByTile = mapOf(1 to 2, 2 to 3),
                maxGrowSlots = 3,
            ),
        )
    }

    @Test fun plantableTiles_skipsEggsAndDecor() {
        assertEquals(
            setOf(0, 2, 4, 5),
            plantable(species = "Carrot", blockedTileIds = setOf(1, 3)),
        )
    }

    /** A potted plant takes a whole tile, so it only goes where nothing grows at all. */
    @Test fun plantableTiles_aPottedPlantOnlyGoesOnAnEmptyTile() {
        assertEquals(
            setOf(0, 3, 5),
            plantable(
                species = null,
                speciesByTile = mapOf(1 to "Carrot", 2 to "Tomato", 4 to "Carrot"),
                plantCountByTile = mapOf(1 to 1, 2 to 1, 4 to 1),
                maxGrowSlots = 3,
            ),
        )
    }

    @Test fun plantableTiles_ignoresOutOfRangeKeys() {
        assertEquals(
            (0 until smallPlot).toSet(),
            plantable(species = "Carrot", blockedTileIds = setOf(-2, smallPlot + 4)),
        )
    }

    @Test fun plantableTiles_fullGardenOffersNothing() {
        assertEquals(
            emptySet<Int>(),
            plantable(species = "Carrot", blockedTileIds = (0 until smallPlot).toSet()),
        )
    }
}
