package com.mgafk.app.data.websocket

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quinoa gameplay actions travel inside a `QuinoaCommand` envelope carrying a
 * requestId and a contiguous `commandSequence` - that pair is what feeds the
 * server's prediction/rollback system.
 *
 * Not all of them, though: the game is migrating its messages one release at a
 * time, and what it still sends flat has to stay flat here too (see
 * GameActions.RAW_MESSAGE_TYPES). Room-scoped messages (Chat, VoteForGame,
 * RestartGame, ...) are not Quinoa commands at all and are never wrapped.
 */
class GameActionsCommandEnvelopeTest {

    private val json = AppJson.default
    private val sent = mutableListOf<String>()
    private val sequencer = CommandSequencer()
    private val actions = GameActions({ sent += it }, sequencer)

    private fun lastMessage() = json.parseToJsonElement(sent.last()).jsonObject

    private fun lastCommand() = lastMessage()["command"]!!.jsonObject

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

    @Test fun `potting mints the id the potted plant will carry`() {
        actions.potPlant(slot = 4)
        val first = lastCommand()
        assertEquals(4, first["slot"]?.jsonPrimitive?.intOrNull)
        val mintedId = first["plantItemId"]?.jsonPrimitive?.contentOrNull
        assertTrue(mintedId.orEmpty().isNotBlank())

        // A caller that already knows the id (to plant the pot straight back) can pass it.
        actions.potPlant(slot = 5, plantItemId = "fixed-id")
        assertEquals("fixed-id", lastCommand()["plantItemId"]?.jsonPrimitive?.contentOrNull)

        // Otherwise every pot gets its own.
        actions.potPlant(slot = 6)
        assertNotEquals(mintedId, lastCommand()["plantItemId"]?.jsonPrimitive?.contentOrNull)
    }

    @Test fun `savePetTeam says whether the team is new`() {
        actions.savePetTeam(teamId = "t1", name = "A", petIds = listOf("p1"), isCreate = true)

        assertWrapped("SavePetTeam")
        assertEquals(true, lastCommand()["isCreate"]?.jsonPrimitive?.booleanOrNull)
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

        actions.sellAllCrops()
        assertWrapped("SellAllCrops")

        actions.putItemInStorage(itemId = "i1", storageId = "PetHutch")
        assertWrapped("PutItemInStorage")

        actions.toggleLockItem(itemId = "i1")
        assertWrapped("ToggleLockItem")

        actions.growEgg(slot = 3, eggId = "e1")
        assertWrapped("GrowEgg")
    }

    private fun assertFlat(type: String) {
        val msg = lastMessage()
        assertEquals(type, msg["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            listOf("Room", "Quinoa"),
            msg["scopePath"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
        assertNull(msg["command"])
        assertNull(msg["commandSequence"])
    }

    @Test fun `ping stays a plain message`() {
        actions.ping(id = 1234L)

        assertFlat("Ping")
        assertEquals(1234L, lastMessage()["id"]?.jsonPrimitive?.longOrNull)
    }

    @Test fun `player position stays a plain message`() {
        actions.move(x = 5.0, y = 6.0)

        assertFlat("PlayerPosition")
    }

    /**
     * The game has not migrated these to the envelope yet, so neither do we - wrapping one
     * would bet on the server having registered it as a command. Re-derive from the bundle
     * when the game moves them (see GameActions.RAW_MESSAGE_TYPES).
     */
    @Test fun `actions the game still sends flat are not wrapped`() {
        actions.teleport(x = 1.0, y = 2.0)
        assertFlat("Teleport")

        actions.setSelectedItem(itemIndex = 3)
        assertFlat("SetSelectedItem")

        actions.checkWeatherStatus()
        assertFlat("CheckWeatherStatus")

        actions.checkFriendBonus()
        assertFlat("CheckFriendBonus")

        actions.throwSnowball()
        assertFlat("ThrowSnowball")

        actions.quinoaTutorialSkipped()
        assertFlat("QuinoaTutorialSkipped")

        actions.requestPetGreet(x = 1.0, y = 2.0)
        assertFlat("RequestPetGreet")

        actions.dropObject()
        assertFlat("DropObject")

        actions.pickupObject()
        assertFlat("PickupObject")

        actions.upgradePetHutch()
        assertFlat("UpgradePetHutch")

        actions.upgradeSeedSilo()
        assertFlat("UpgradeSeedSilo")

        actions.upgradeDecorShed()
        assertFlat("UpgradeDecorShed")
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

    @Test fun `actions added from the live bundle carry its field names`() {
        actions.preserve(itemId = "i1", growSlotIdx = 2)
        assertWrapped("Preserve")
        assertEquals(2, lastCommand()["growSlotIdx"]?.jsonPrimitive?.intOrNull)

        actions.swapItemWithStorage(storageId = "PetHutch", inventoryItemId = "i1", storageItemId = "s1")
        assertWrapped("SwapItemWithStorage")
        assertEquals("s1", lastCommand()["storageItemId"]?.jsonPrimitive?.contentOrNull)

        actions.applyPetTeam(teamId = "t1")
        assertWrapped("ApplyPetTeam")
        assertEquals("t1", lastCommand()["teamId"]?.jsonPrimitive?.contentOrNull)

        actions.savePetTeam(teamId = "t1", name = "A", petIds = listOf("p1", "p2"), isCreate = false)
        assertWrapped("SavePetTeam")
        assertEquals(2, lastCommand()["petIds"]?.jsonArray?.size)

        actions.equipPetCosmetic(petItemId = "p1", slotCategory = "Hat", cosmeticId = "c1")
        assertWrapped("EquipPetCosmetic")
        assertEquals("Hat", lastCommand()["slotCategory"]?.jsonPrimitive?.contentOrNull)
    }

    @Test fun `markChatRead is room scoped`() {
        actions.markChatRead(seq = 12)

        val msg = lastMessage()
        assertEquals("MarkChatRead", msg["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(listOf("Room"), msg["scopePath"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals(12, msg["seq"]?.jsonPrimitive?.intOrNull)
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
        actions.teleport(x = 1.0, y = 1.0)
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
