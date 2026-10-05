package com.mgafk.app.data.websocket.state

import com.mgafk.app.data.model.ShopSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bundle 1422 dropped `secondsUntilRestock` from the shop state: each shop now carries the
 * `deadlineMs` its stock runs out at, and the game counts down to it itself. Reading the old
 * field left every timer at 00:00.
 */
class ShopRestockCountdownTest {

    private fun obj(raw: String) = Json.parseToJsonElement(raw) as JsonObject

    private val deadline = 1790325000000L

    private val seedShop = obj(
        """
        {
          "restockId": "seed:5967749",
          "startedAtMs": 1790324700000,
          "deadlineMs": $deadline,
          "inventory": [{"itemType": "Seed", "species": "Carrot", "initialStock": 10}]
        }
        """,
    )

    @Test
    fun `reads the deadline the server sends`() {
        assertEquals(deadline, ShopModel.fromState("seed", seedShop).deadlineMs)
    }

    @Test
    fun `counts down to the deadline in whole seconds, rounded up like the game`() {
        val shop = ShopSnapshot(restockId = "seed:5967749", deadlineMs = deadline)
        assertEquals(280, shop.secondsUntilRestock(nowMs = deadline - 280_000))
        assertEquals(280, shop.secondsUntilRestock(nowMs = deadline - 279_001))
        assertEquals(1, shop.secondsUntilRestock(nowMs = deadline - 1))
    }

    @Test
    fun `never goes below zero once the deadline has passed`() {
        val shop = ShopSnapshot(restockId = "seed:5967749", deadlineMs = deadline)
        assertEquals(0, shop.secondsUntilRestock(nowMs = deadline))
        assertEquals(0, shop.secondsUntilRestock(nowMs = deadline + 5_000))
    }

    @Test
    fun `a closed shop has no countdown`() {
        val shop = ShopSnapshot(restockId = null, deadlineMs = deadline)
        assertEquals(0, shop.secondsUntilRestock(nowMs = deadline - 60_000))
    }
}
