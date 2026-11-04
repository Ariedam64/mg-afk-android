package com.mgafk.app.data.repository

import com.mgafk.app.data.model.InventoryDecorItem
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.model.InventorySnapshot
import com.mgafk.app.data.model.Session
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * BR-28: the game holds 100 inventory entries, and a stackable item only fits a full
 * inventory when its stack is already there. The game client checks it before sending
 * PurchaseShopItem; the app did not, so a tile stayed buyable, the server rejected the
 * purchase and the tile came back 5 s later with "Purchase failed".
 */
class ShopBuyabilityInventoryFullTest {

    private val decorId = "StoneBench"
    private val seedId = "Carrot"

    @Suppress("UNCHECKED_CAST")
    private val cache: ConcurrentHashMap<String, LinkedHashMap<String, MgApi.GameEntry>> =
        MgApi::class.java.getDeclaredField("cache").run {
            isAccessible = true
            get(null) as ConcurrentHashMap<String, LinkedHashMap<String, MgApi.GameEntry>>
        }

    private val seeded = listOf("decors", "plants")
    private val previous = mutableMapOf<String, LinkedHashMap<String, MgApi.GameEntry>?>()

    @Before
    fun seedCatalogue() {
        seeded.forEach { previous[it] = cache[it] }
        cache["decors"] = linkedMapOf(decorId to MgApi.GameEntry(id = decorId, name = "Stone Bench", sprite = null))
        cache["plants"] = linkedMapOf(seedId to MgApi.GameEntry(id = seedId, name = "Carrot", sprite = null))
    }

    @After
    fun restoreCatalogue() {
        seeded.forEach { cat ->
            val old = previous[cat]
            if (old == null) cache.remove(cat) else cache[cat] = old
        }
    }

    /** [pets] non-stackable entries, plus whatever stacks are given. */
    private fun session(
        pets: Int,
        decors: List<InventoryDecorItem> = emptyList(),
        seeds: List<InventorySeedItem> = emptyList(),
    ) = Session(
        inventory = InventorySnapshot(
            pets = List(pets) { InventoryPetItem(id = "pet-$it", petSpecies = "Bunny") },
            decors = decors,
            seeds = seeds,
        ),
    )

    @Test
    fun `a decor without a stack cannot be bought into a full inventory`() {
        val state = session(pets = 100).buyState(decorId)
        assertEquals("InventoryFull", state.name)
    }

    @Test
    fun `a seed without a stack cannot be bought into a full inventory`() {
        val state = session(pets = 100).buyState(seedId)
        assertEquals("InventoryFull", state.name)
    }

    @Test
    fun `a decor whose stack is already there still fits a full inventory`() {
        val full = session(pets = 99, decors = listOf(InventoryDecorItem(decorId = decorId, quantity = 3)))
        assertEquals(ShopItemBuyState.Buyable, full.buyState(decorId))
    }

    @Test
    fun `one free slot is enough for a new decor`() {
        assertEquals(ShopItemBuyState.Buyable, session(pets = 99).buyState(decorId))
    }
}
