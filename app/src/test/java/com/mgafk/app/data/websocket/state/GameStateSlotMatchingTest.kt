package com.mgafk.app.data.websocket.state

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The game renamed the userSlot owner field from `playerId` to `userId`,
 * leaving `playerId` null on every slot. Matching on the old name alone
 * resolves no slot at all, which empties garden/inventory/storage silently.
 */
class GameStateSlotMatchingTest {

    private val json = AppJson.default

    private fun welcome(slots: String, players: String = DEFAULT_PLAYERS): String = """
        {"type":"Welcome","fullState":{"scope":"Room",
         "data":{"players":[$players]},
         "child":{"data":{"userSlots":[$slots]}}}}
    """.trimIndent()

    private fun stateFrom(raw: String): GameState =
        GameState().apply { handleMessage(json.parseToJsonElement(raw).jsonObject) }

    @Test fun `slot is matched by its userId`() {
        val state = stateFrom(welcome("""null,{"type":"user","userId":"P1","data":{"coinsCount":42}}"""))

        val me = state.getPlayer("P1")
        assertEquals(1, me?.slotIndex)
        assertEquals(42.0, me?.coins ?: 0.0, 0.0)
        assertEquals(1, state.findUserSlotIndex("P1"))
    }

    @Test fun `legacy slots matched by playerId still work`() {
        val state = stateFrom(welcome("""{"type":"user","playerId":"P1","data":{"coinsCount":7}}"""))

        assertEquals(0, state.getPlayer("P1")?.slotIndex)
        assertEquals(7.0, state.getPlayer("P1")?.coins ?: 0.0, 0.0)
    }

    @Test fun `the right slot is picked in a lobby where every slot has data`() {
        val slots = listOf("OTHER1", "OTHER2", "OTHER3", "P1").joinToString(",") { owner ->
            """{"type":"user","userId":"$owner","data":{"coinsCount":${owner.length}}}"""
        }

        val state = stateFrom(welcome(slots))

        assertEquals(3, state.getPlayer("P1")?.slotIndex)
        assertEquals(2.0, state.getPlayer("P1")?.coins ?: 0.0, 0.0)
    }

    @Test fun `no slot is claimed when none belongs to the player`() {
        val state = stateFrom(welcome("""{"type":"user","userId":"SOMEONE_ELSE","data":{"coinsCount":9}}"""))

        assertNull(state.getPlayer("P1")?.slotIndex)
        assertNull(state.findUserSlotIndex("P1"))
    }

    @Test fun `an empty player id never claims a slot`() {
        val state = stateFrom(welcome("""{"type":"user","data":{"coinsCount":9}}"""))

        assertNull(state.findUserSlotIndex(""))
    }

    companion object {
        private const val DEFAULT_PLAYERS =
            """{"id":"P1","name":"Me","isConnected":true,"discordUserId":"D1"}"""
    }
}
