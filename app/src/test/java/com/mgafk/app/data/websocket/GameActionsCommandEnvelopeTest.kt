package com.mgafk.app.data.websocket

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server rejects `HarvestCrop`, `PotPlant`, `Preserve`, `PurchaseShopItem`
 * and `EquipPetCosmetic` unless they arrive inside a `QuinoaCommand` envelope
 * carrying a contiguous `commandSequence`. Every other action must stay a
 * plain message - wrapping one of those would break it in the other direction.
 */
class GameActionsCommandEnvelopeTest {

    private val json = AppJson.default
    private val sent = mutableListOf<String>()
    private val sequencer = CommandSequencer()
    private val actions = GameActions({ sent += it }, sequencer)

    private fun lastMessage() = json.parseToJsonElement(sent.last()).jsonObject

    @Test fun `harvest is wrapped in a QuinoaCommand envelope`() {
        sequencer.seed(7)

        actions.harvestCrop(slot = 12, slotsIndex = 3)

        val msg = lastMessage()
        assertEquals("QuinoaCommand", msg["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            listOf("Room", "Quinoa"),
            msg["scopePath"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
        assertEquals(8L, msg["commandSequence"]?.jsonPrimitive?.longOrNull)
        assertTrue(msg["requestId"]?.jsonPrimitive?.contentOrNull.orEmpty().isNotBlank())

        val command = msg["command"]?.jsonObject
        assertEquals("HarvestCrop", command?.get("type")?.jsonPrimitive?.contentOrNull)
        assertEquals(12, command?.get("slot")?.jsonPrimitive?.intOrNull)
        assertEquals(3, command?.get("slotsIndex")?.jsonPrimitive?.intOrNull)
    }

    @Test fun `potting is wrapped too`() {
        sequencer.seed(0)

        actions.potPlant(slot = 4)

        val msg = lastMessage()
        assertEquals("QuinoaCommand", msg["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1L, msg["commandSequence"]?.jsonPrimitive?.longOrNull)
        assertEquals("PotPlant", msg["command"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull)
    }

    @Test fun `unwrapped actions keep the plain message shape`() {
        sequencer.seed(7)

        actions.plantSeed(slot = 2, species = "Carrot")

        val msg = lastMessage()
        assertEquals("PlantSeed", msg["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(2, msg["slot"]?.jsonPrimitive?.intOrNull)
        assertNull(msg["commandSequence"])
        assertNull(msg["command"])
    }

    @Test fun `sequence numbers stay contiguous across wrapped commands`() {
        sequencer.seed(41)

        actions.harvestCrop(slot = 1)
        actions.potPlant(slot = 2)
        actions.harvestCrop(slot = 3)

        val sequences = sent.map {
            json.parseToJsonElement(it).jsonObject["commandSequence"]?.jsonPrimitive?.longOrNull
        }
        assertEquals(listOf(42L, 43L, 44L), sequences)
    }

    @Test fun `unwrapped actions do not consume a sequence number`() {
        sequencer.seed(10)

        actions.harvestCrop(slot = 1)
        actions.plantSeed(slot = 2, species = "Carrot")
        actions.harvestCrop(slot = 3)

        val sequences = sent.mapNotNull {
            json.parseToJsonElement(it).jsonObject["commandSequence"]?.jsonPrimitive?.longOrNull
        }
        assertEquals(listOf(11L, 12L), sequences)
    }

    @Test fun `each Welcome re-seeds the counter`() {
        sequencer.seed(5)
        actions.harvestCrop(slot = 1)

        // Reconnect: the server reports the sequence it has actually executed.
        sequencer.seed(2)
        actions.harvestCrop(slot = 1)

        assertEquals(3L, lastMessage()["commandSequence"]?.jsonPrimitive?.longOrNull)
    }

    @Test fun `a fresh connection starts at one before any Welcome`() {
        sequencer.seed(99)
        sequencer.reset()

        actions.harvestCrop(slot = 1)

        assertEquals(1L, lastMessage()["commandSequence"]?.jsonPrimitive?.longOrNull)
    }

    @Test fun `each wrapped command gets its own requestId`() {
        actions.harvestCrop(slot = 1)
        actions.harvestCrop(slot = 2)

        val requestIds = sent.map {
            json.parseToJsonElement(it).jsonObject["requestId"]?.jsonPrimitive?.contentOrNull
        }
        assertEquals(2, requestIds.toSet().size)
    }
}
