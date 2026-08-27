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
 * Every Quinoa gameplay action now travels inside a `QuinoaCommand` envelope
 * carrying a requestId and a contiguous `commandSequence` - that pair is what
 * feeds the server's prediction/rollback system. The flat
 * `{scopePath, type, ...params}` form is still honoured but is being removed.
 *
 * Two exceptions stay flat: `Ping` has its own Pong reply and `PlayerPosition`
 * feeds the movement snapshot channel, neither goes through the command
 * pipeline. Room-scoped messages (Chat, VoteForGame, RestartGame, ...) are not
 * Quinoa commands at all and are never wrapped.
 */
class GameActionsCommandEnvelopeTest {

    private val json = AppJson.default
    private val sent = mutableListOf<String>()
    private val sequencer = CommandSequencer()
    private val actions = GameActions({ sent += it }, sequencer)

    private fun lastMessage() = json.parseToJsonElement(sent.last()).jsonObject

    private fun assertWrapped(commandType: String) {
        val msg = lastMessage()
        assertEquals("QuinoaCommand", msg["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            listOf("Room", "Quinoa"),
            msg["scopePath"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
        assertTrue(msg["requestId"]?.jsonPrimitive?.contentOrNull.orEmpty().isNotBlank())
        assertEquals(commandType, msg["command"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull)
    }

    @Test fun `harvest is wrapped in a QuinoaCommand envelope`() {
        sequencer.seed(7)

        actions.harvestCrop(slot = 12, slotsIndex = 3)

        val msg = lastMessage()
        assertWrapped("HarvestCrop")
        assertEquals(8L, msg["commandSequence"]?.jsonPrimitive?.longOrNull)

        val command = msg["command"]?.jsonObject
        assertEquals(12, command?.get("slot")?.jsonPrimitive?.intOrNull)
        assertEquals(3, command?.get("slotsIndex")?.jsonPrimitive?.intOrNull)
    }

    @Test fun `potting is wrapped too`() {
        sequencer.seed(0)

        actions.potPlant(slot = 4)

        assertWrapped("PotPlant")
        assertEquals(1L, lastMessage()["commandSequence"]?.jsonPrimitive?.longOrNull)
    }

    @Test fun `planting is wrapped`() {
        sequencer.seed(7)

        actions.plantSeed(slot = 2, species = "Carrot")

        val msg = lastMessage()
        assertWrapped("PlantSeed")
        assertEquals(8L, msg["commandSequence"]?.jsonPrimitive?.longOrNull)

        val command = msg["command"]?.jsonObject
        assertEquals(2, command?.get("slot")?.jsonPrimitive?.intOrNull)
        assertEquals("Carrot", command?.get("species")?.jsonPrimitive?.contentOrNull)
        // The params belong to the command, not to the envelope.
        assertNull(msg["slot"])
        assertNull(msg["species"])
    }

    @Test fun `every other gameplay action is wrapped as well`() {
        actions.feedPet(petItemId = "pet_1", cropItemId = "crop_1")
        assertWrapped("FeedPet")

        actions.teleport(x = 10.0, y = 20.0)
        assertWrapped("Teleport")

        actions.sellAllCrops()
        assertWrapped("SellAllCrops")

        actions.putItemInStorage(itemId = "i1", storageId = "PetHutch")
        assertWrapped("PutItemInStorage")

        actions.toggleLockItem(itemId = "i1")
        assertWrapped("ToggleLockItem")

        actions.growEgg(slot = 3, eggId = "e1")
        assertWrapped("GrowEgg")
    }

    @Test fun `ping stays a plain message`() {
        actions.ping(id = 1234L)

        val msg = lastMessage()
        assertEquals("Ping", msg["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1234L, msg["id"]?.jsonPrimitive?.longOrNull)
        assertNull(msg["command"])
        assertNull(msg["commandSequence"])
    }

    @Test fun `player position stays a plain message`() {
        actions.move(x = 5.0, y = 6.0)

        val msg = lastMessage()
        assertEquals("PlayerPosition", msg["type"]?.jsonPrimitive?.contentOrNull)
        assertNull(msg["command"])
        assertNull(msg["commandSequence"])
    }

    @Test fun `room scoped messages are never wrapped`() {
        actions.chat("hello")

        val msg = lastMessage()
        assertEquals("Chat", msg["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(listOf("Room"), msg["scopePath"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertNull(msg["commandSequence"])
    }

    @Test fun `restartGame is room scoped and names the game`() {
        actions.restartGame()

        val msg = lastMessage()
        assertEquals("RestartGame", msg["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(listOf("Room"), msg["scopePath"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals("Quinoa", msg["name"]?.jsonPrimitive?.contentOrNull)
    }

    @Test fun `usurpHost is room scoped`() {
        actions.usurpHost()

        val msg = lastMessage()
        assertEquals("UsurpHost", msg["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(listOf("Room"), msg["scopePath"]?.jsonArray?.map { it.jsonPrimitive.content })
    }

    @Test fun `kickPlayer names the target with the field the game uses`() {
        actions.kickPlayer("p_123")

        val msg = lastMessage()
        assertEquals("KickPlayer", msg["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("p_123", msg["targetPlayerId"]?.jsonPrimitive?.contentOrNull)
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

    @Test fun `plain messages do not consume a sequence number`() {
        sequencer.seed(10)

        actions.harvestCrop(slot = 1)
        actions.ping(id = 1L)
        actions.move(x = 1.0, y = 1.0)
        actions.chat("hi")
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
