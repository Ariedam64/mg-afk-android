package com.mgafk.app.data.repository

import com.mgafk.app.data.model.ShopSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ShopStockTest {

    private val shops = listOf(
        ShopSnapshot(type = "seed", itemStocks = mapOf("Carrot" to 5, "Tomato" to 3)),
        ShopSnapshot(type = "tool", itemStocks = mapOf("Shovel" to 1)),
    )

    private fun stock(shops: List<ShopSnapshot>, type: String, item: String) =
        shops.first { it.type == type }.itemStocks[item]

    @Test
    fun `taking lowers one item and never goes below zero`() {
        val after = ShopStock.take(shops, "seed", "Carrot", 2)
        assertEquals(3, stock(after, "seed", "Carrot"))
        assertEquals(3, stock(after, "seed", "Tomato"))
        assertEquals(0, stock(ShopStock.take(shops, "tool", "Shovel", 4), "tool", "Shovel"))
    }

    @Test
    fun `giving back a failed purchase keeps the other purchases made since`() {
        val afterCarrot = ShopStock.take(shops, "seed", "Carrot", 1)
        val afterBoth = ShopStock.take(afterCarrot, "seed", "Tomato", 1)

        val carrotFailed = ShopStock.giveBack(afterBoth, "seed", "Carrot", 1)

        assertEquals(5, stock(carrotFailed, "seed", "Carrot"))
        assertEquals(2, stock(carrotFailed, "seed", "Tomato"))
    }

    @Test
    fun `giving back an item the shop no longer lists changes nothing`() {
        val restocked = listOf(ShopSnapshot(type = "seed", itemStocks = mapOf("Pepper" to 4)))
        assertEquals(restocked, ShopStock.giveBack(restocked, "seed", "Carrot", 1))
    }
}
