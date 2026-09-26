package com.mgafk.app.data.websocket.state

import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Since game version 1284 `shopPurchases[shop]` is `{ restockId, startedAtMs, purchases }` and
 * is no longer cleared at restock. Counting last cycle's purchases against the new stock made
 * restocked eggs and Crop Cleansers appear, then go blank on the next update.
 */
class ShopPurchasesRestockTest {

    private val json = AppJson.default

    private fun shop(restockId: String?) = ShopModel.fromState(
        "egg",
        json.parseToJsonElement(
            """{"restockId":${restockId?.let { "\"$it\"" } ?: "null"},"startedAtMs":2000,
               "secondsUntilRestock":300,
               "inventory":[{"itemType":"Egg","eggId":"MythicalEgg","initialStock":2}]}"""
        ).jsonObject,
    )

    private fun entry(restockId: String) = json.parseToJsonElement(
        """{"restockId":"$restockId","startedAtMs":1000,"purchases":{"MythicalEgg":2}}"""
    ).jsonObject

    @Test fun `purchases from the current restock count`() {
        val bought = shop("egg:2").purchasesThisRestock(entry("egg:2"))
        assertEquals(2, bought?.get("MythicalEgg")?.jsonPrimitive?.int)
    }

    @Test fun `last restock's purchases do not empty the new stock`() {
        assertNull(shop("egg:2").purchasesThisRestock(entry("egg:1")))
    }

    @Test fun `a shop without a restock yet has no known purchases`() {
        assertNull(shop(null).purchasesThisRestock(entry("egg:1")))
    }

    @Test fun `no entry means nothing bought`() {
        assertNull(shop("egg:2").purchasesThisRestock(null))
    }

    @Test fun `the shop's restockId is read from the game state`() {
        assertEquals("egg:2", shop("egg:2").restockId)
    }
}
