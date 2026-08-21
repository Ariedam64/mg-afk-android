package com.mgafk.app.data.repository

/**
 * Occupancy math for a player's dirt tiles.
 *
 * A garden's `tileObjects` map only contains the tiles that hold something (plant, egg or decor);
 * empty tiles are simply absent. So free tiles can only be derived against the garden's real
 * capacity - never against the highest occupied key, which ignores every empty tile above it and
 * jumps around as the top-most tile gets planted/harvested.
 */
object GardenTiles {

    /**
     * Dirt tiles every garden has (10 rows x 20 cols), i.e. its total planting capacity. Fixed by
     * the world layout: the game builds each slot's dirt-tile list from its Tiled map (the same
     * source the embedded garden-map data comes from) and it never changes at runtime. Boardwalk
     * tiles are a separate list and are NOT plantable.
     */
    const val DIRT_TILES_PER_GARDEN = 200

    /** Tiles with nothing on them, out of [capacity] dirt tiles. */
    fun freeTileCount(occupiedTileIds: Set<Int>, capacity: Int = DIRT_TILES_PER_GARDEN): Int {
        if (capacity <= 0) return 0
        val occupiedInRange = occupiedTileIds.count { it in 0 until capacity }
        return capacity - occupiedInRange
    }

    /** Lowest empty tile index, or null when the garden is full. */
    fun firstFreeTile(occupiedTileIds: Set<Int>, capacity: Int = DIRT_TILES_PER_GARDEN): Int? =
        (0 until capacity).firstOrNull { it !in occupiedTileIds }
}
