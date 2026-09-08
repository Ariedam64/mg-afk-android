package com.mgafk.app.data.websocket

import com.mgafk.app.data.AppJson
import com.mgafk.app.data.model.CrystalShard
import com.mgafk.app.data.model.CrystalType
import com.mgafk.app.data.model.GardenTileType
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crystal commands, checked against the validation schema the game bundle ships:
 *
 *   PlaceCrystal  { tileType, localTileIndex, item: {itemType:"Tool", toolId | itemId},
 *                   intent: {type:"place"} | {type:"merge", mergeGainSeconds} }
 *   PickupCrystal { tileType, localTileIndex, crystalType, itemId }
 *
 * The shapes matter more than usual here: the app cannot try them against a real garden
 * without spending a shard, so the schema is the reference.
 */
class GameActionsCrystalTest {

    private val json = AppJson.default
    private val sent = mutableListOf<String>()
    private val actions = GameActions({ sent += it })

    private fun lastCommand() = json.parseToJsonElement(sent.last()).jsonObject["command"]!!.jsonObject

    @Test fun `planting a shard from a stack names it by tool id`() {
        actions.placeCrystal(
            shard = CrystalShard.Stacked("XPShard"),
            tileType = GardenTileType.Dirt,
            localTileIndex = 40,
        )

        val command = lastCommand()
        assertEquals("PlaceCrystal", command["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Dirt", command["tileType"]?.jsonPrimitive?.contentOrNull)
        assertEquals(40, command["localTileIndex"]?.jsonPrimitive?.intOrNull)

        val item = command["item"]?.jsonObject
        assertEquals("Tool", item?.get("itemType")?.jsonPrimitive?.contentOrNull)
        assertEquals("XPShard", item?.get("toolId")?.jsonPrimitive?.contentOrNull)
        assertNull("a stacked shard has no item id", item?.get("itemId"))

        assertEquals("place", command["intent"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull)
    }

    /** A shard picked back up is a unique item, so it travels by id and keeps its time. */
    @Test fun `planting a used shard names it by item id`() {
        actions.placeCrystal(
            shard = CrystalShard.Used("6f1c-shard"),
            tileType = GardenTileType.Boardwalk,
            localTileIndex = 60,
        )

        val item = lastCommand()["item"]?.jsonObject
        assertEquals("Tool", item?.get("itemType")?.jsonPrimitive?.contentOrNull)
        assertEquals("6f1c-shard", item?.get("itemId")?.jsonPrimitive?.contentOrNull)
        assertNull("a used shard has no tool id", item?.get("toolId"))
    }

    @Test fun `the boardwalk is a tile type of its own`() {
        actions.placeCrystal(CrystalShard.Stacked("RainWardShard"), GardenTileType.Boardwalk, 61)

        assertEquals("Boardwalk", lastCommand()["tileType"]?.jsonPrimitive?.contentOrNull)
    }

    /** Fusing is the same command with a merge intent, not a command of its own. */
    @Test fun `fusing sends PlaceCrystal with the gain it asks for`() {
        actions.fuseCrystal(
            shard = CrystalShard.Stacked("RainWardShard"),
            tileType = GardenTileType.Boardwalk,
            localTileIndex = 60,
            mergeGainSeconds = 8_200,
        )

        val command = lastCommand()
        assertEquals("PlaceCrystal", command["type"]?.jsonPrimitive?.contentOrNull)
        val intent = command["intent"]?.jsonObject
        assertEquals("merge", intent?.get("type")?.jsonPrimitive?.contentOrNull)
        assertEquals(8_200, intent?.get("mergeGainSeconds")?.jsonPrimitive?.intOrNull)
    }

    @Test fun `picking up names the crystal and mints the item it becomes`() {
        actions.pickupCrystal(
            crystalType = CrystalType.RainWard,
            tileType = GardenTileType.Boardwalk,
            localTileIndex = 61,
        )

        val command = lastCommand()
        assertEquals("PickupCrystal", command["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Boardwalk", command["tileType"]?.jsonPrimitive?.contentOrNull)
        assertEquals(61, command["localTileIndex"]?.jsonPrimitive?.intOrNull)
        assertEquals("RainWard", command["crystalType"]?.jsonPrimitive?.contentOrNull)
        assertTrue(command["itemId"]?.jsonPrimitive?.contentOrNull.orEmpty().isNotBlank())
    }

    @Test fun `each pickup mints its own item id`() {
        actions.pickupCrystal(CrystalType.XP, GardenTileType.Dirt, 40)
        val first = lastCommand()["itemId"]?.jsonPrimitive?.contentOrNull
        actions.pickupCrystal(CrystalType.XP, GardenTileType.Dirt, 41)

        assertTrue(first != lastCommand()["itemId"]?.jsonPrimitive?.contentOrNull)
    }
}
