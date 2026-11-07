package com.mgafk.app.data.repository

import com.mgafk.app.data.AppJson
import com.mgafk.app.data.model.Session
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * BR-36: the game added a tool ring (`inventory.toolRing`, 5 pins at most). A pinned tool
 * stays in `inventory.items`, but the game leaves it out of the 100-entry bag limit
 * (`tn()` / `xn()` in inventoryAtoms, bundle 1953). The app counted every entry, so a player
 * with 101 entries, 5 of them pinned (96 for the game), saw every seed without a stack as FULL.
 *
 * The session is decoded from its stored JSON so this test compiles before the inventory
 * snapshot knows about the ring: the pinned tools are listed under
 * `inventory.toolRingIds` (the tools' [com.mgafk.app.data.model.InventoryToolItem.storageKey]),
 * which the current model silently drops.
 */
class ShopBuyabilityToolRingTest {

    private val seedId = "Carrot"

    @Suppress("UNCHECKED_CAST")
    private val cache: ConcurrentHashMap<String, LinkedHashMap<String, MgApi.GameEntry>> =
        MgApi::class.java.getDeclaredField("cache").run {
            isAccessible = true
            get(null) as ConcurrentHashMap<String, LinkedHashMap<String, MgApi.GameEntry>>
        }

    private val seeded = listOf("plants")
    private val previous = mutableMapOf<String, LinkedHashMap<String, MgApi.GameEntry>?>()

    @Before
    fun seedCatalogue() {
        seeded.forEach { previous[it] = cache[it] }
        cache["plants"] = linkedMapOf(seedId to MgApi.GameEntry(id = seedId, name = "Carrot", sprite = null))
    }

    @After
    fun restoreCatalogue() {
        seeded.forEach { cat ->
            val old = previous[cat]
            if (old == null) cache.remove(cat) else cache[cat] = old
        }
    }

    /** [pets] non-stackable entries, one stack per tool in [tools], and the ring's pins. */
    private fun session(pets: Int, tools: List<String>, pinned: List<String>): Session {
        val petJson = List(pets) { """{"id":"pet-$it","petSpecies":"Bunny"}""" }.joinToString(",")
        val toolJson = tools.joinToString(",") { """{"toolId":"$it","quantity":1}""" }
        val pinJson = pinned.joinToString(",") { "\"$it\"" }
        val raw = """
            {"inventory":{
              "pets":[$petJson],
              "tools":[$toolJson],
              "toolRingIds":[$pinJson]
            }}
        """.trimIndent()
        return AppJson.storage.decodeFromString(Session.serializer(), raw)
    }

    private val ringTools = listOf("PlanterPot", "WateringCan", "Camera", "Shovel", "CropCleanser")

    @Test
    fun `tools pinned in the ring do not count toward the 100-entry limit`() {
        // The player's case: 101 entries, 5 of them pinned, so 96 for the game.
        val state = session(pets = 96, tools = ringTools, pinned = ringTools)
        assertEquals(101, state.inventory.pets.size + state.inventory.tools.size)
        assertEquals(ShopItemBuyState.Buyable, state.buyState(seedId))
    }

    @Test
    fun `without pins a 100-entry inventory is still full`() {
        val state = session(pets = 95, tools = ringTools, pinned = emptyList())
        assertEquals(ShopItemBuyState.InventoryFull, state.buyState(seedId))
    }

    @Test
    fun `a pin whose tool is not in the inventory frees no slot`() {
        val state = session(pets = 100, tools = emptyList(), pinned = listOf("Camera"))
        assertEquals(ShopItemBuyState.InventoryFull, state.buyState(seedId))
    }

    @Test
    fun `the ring holds 5 tools at most`() {
        // 105 entries, 6 pins: only 5 of them count, which leaves exactly 100.
        val six = ringTools + "Trowel"
        val state = session(pets = 99, tools = six, pinned = six)
        assertEquals(ShopItemBuyState.InventoryFull, state.buyState(seedId))
    }
}
