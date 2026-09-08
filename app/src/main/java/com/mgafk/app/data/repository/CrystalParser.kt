package com.mgafk.app.data.repository

import com.mgafk.app.data.model.CrystalType
import com.mgafk.app.data.model.GardenTileRef
import com.mgafk.app.data.model.GardenTileType
import com.mgafk.app.data.model.PlacedCrystal
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the crystals standing in a garden out of its two tile maps.
 *
 * Both maps are keyed by tile index and hold every kind of object, so a crystal is picked out
 * by its `objectType`. A crystal kind this build does not know is skipped: the garden is worth
 * showing even when the game has added something new.
 */
object CrystalParser {

    private const val CRYSTAL_OBJECT_TYPE = "crystal"

    fun parse(dirtTiles: JsonObject?, boardwalkTiles: JsonObject?): List<PlacedCrystal> =
        read(dirtTiles, GardenTileType.Dirt) + read(boardwalkTiles, GardenTileType.Boardwalk)

    /**
     * Every tile of either map that already holds something, whatever that is.
     *
     * A crystal only plants on an empty tile, and the app cannot tell the player why the game
     * refused, so it is better placed to not offer the tile at all.
     */
    fun occupiedTiles(dirtTiles: JsonObject?, boardwalkTiles: JsonObject?): Set<GardenTileRef> =
        refs(dirtTiles, GardenTileType.Dirt) + refs(boardwalkTiles, GardenTileType.Boardwalk)

    private fun refs(tiles: JsonObject?, tileType: GardenTileType): Set<GardenTileRef> =
        tiles.orEmpty().keys.mapNotNullTo(mutableSetOf()) { key ->
            key.toIntOrNull()?.let { GardenTileRef(tileType, it) }
        }

    private fun read(tiles: JsonObject?, tileType: GardenTileType): List<PlacedCrystal> =
        tiles.orEmpty().entries.mapNotNull { (key, value) ->
            val tile = value as? JsonObject ?: return@mapNotNull null
            if (tile.string("objectType") != CRYSTAL_OBJECT_TYPE) return@mapNotNull null
            val type = CrystalType.fromId(tile.string("crystalType")) ?: return@mapNotNull null
            PlacedCrystal(
                type = type,
                tileType = tileType,
                localTileIndex = key.toIntOrNull() ?: return@mapNotNull null,
                remainingSeconds = tile["remainingActiveSeconds"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }

    private fun JsonObject?.orEmpty(): Map<String, kotlinx.serialization.json.JsonElement> =
        this ?: emptyMap()

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}
