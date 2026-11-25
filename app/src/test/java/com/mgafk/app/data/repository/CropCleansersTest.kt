package com.mgafk.app.data.repository

import com.mgafk.app.data.model.InventoryToolItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** BR-67: the Hydro and Lunar cleansers of game 2198, and which crops each can cleanse. */
class CropCleansersTest {

    private val noCatalog: (String) -> MgApi.GameEntry? = { null }
    private val noMutations: (String) -> MgApi.MutationEntry? = { null }

    private fun offer(toolId: String, quantity: Int = 1) =
        CropCleansers.Offer(toolId, quantity, CropCleansers.groupsCleared(toolId, noCatalog))

    @Test fun `without MG API the groups come from the fallback`() {
        assertEquals(listOf("Hydro"), CropCleansers.groupsCleared("HydroCleanser", noCatalog))
        assertEquals(listOf("Lunar"), CropCleansers.groupsCleared("LunarCleanser", noCatalog))
        assertEquals(listOf("Hydro", "Lunar"), CropCleansers.groupsCleared("CropCleanser", noCatalog))
        assertEquals("Hydro", CropCleansers.mutationGroup("Thunderstruck", noMutations))
        assertEquals("Lunar", CropCleansers.mutationGroup("Dawncharged", noMutations))
    }

    @Test fun `the catalogue wins over the fallback`() {
        val catalog: (String) -> MgApi.GameEntry? = { id ->
            if (id == "NewCleanser") MgApi.GameEntry(id = id, name = id, sprite = null, clearsMutationGroups = listOf("Growth"))
            else null
        }
        assertEquals(listOf("Growth"), CropCleansers.groupsCleared("NewCleanser", catalog))
        val mutations: (String) -> MgApi.MutationEntry? = { MgApi.MutationEntry(it, 1.0, group = "Lunar") }
        assertEquals("Lunar", CropCleansers.mutationGroup("Wet", mutations))
    }

    @Test fun `offers merge stacks and keep only cleansers in Crop Hydro Lunar order`() {
        val tools = listOf(
            InventoryToolItem(toolId = "LunarCleanser", quantity = 1),
            InventoryToolItem(toolId = "WateringCan", quantity = 4),
            InventoryToolItem(toolId = "HydroCleanser", quantity = 2),
            InventoryToolItem(toolId = "CropCleanser", quantity = 1),
            InventoryToolItem(toolId = "HydroCleanser", quantity = 3),
        )
        val offers = CropCleansers.offers(tools, noCatalog)
        assertEquals(listOf("CropCleanser", "HydroCleanser", "LunarCleanser"), offers.map { it.toolId })
        assertEquals(listOf(1, 5, 1), offers.map { it.quantity })
    }

    @Test fun `each cleanser applies only to its mutation groups`() {
        val hydro = offer("HydroCleanser")
        val lunar = offer("LunarCleanser")
        val crop = offer("CropCleanser")
        assertTrue(CropCleansers.canCleanse(hydro, listOf("Wet"), noMutations))
        assertTrue(CropCleansers.canCleanse(hydro, listOf("Thundercharged"), noMutations))
        assertFalse(CropCleansers.canCleanse(hydro, listOf("Dawnlit"), noMutations))
        assertTrue(CropCleansers.canCleanse(lunar, listOf("Ambershine"), noMutations))
        assertTrue(CropCleansers.canCleanse(crop, listOf("Ambershine"), noMutations))
        assertTrue(CropCleansers.canCleanse(crop, listOf("Frozen"), noMutations))
        for (cleanser in listOf(hydro, lunar, crop)) {
            assertFalse(CropCleansers.canCleanse(cleanser, listOf("Gold"), noMutations))
            assertFalse(CropCleansers.canCleanse(cleanser, listOf("Rainbow"), noMutations))
        }
    }

    @Test fun `the picker is disabled when no held cleanser applies`() {
        val state = CropCleansers.pickerState(listOf(offer("HydroCleanser", 2)), listOf("Gold"), noMutations) { it }
        assertFalse(state.enabled)
        assertEquals("HydroCleanser x2", state.items.single().label)
        val wet = CropCleansers.pickerState(listOf(offer("HydroCleanser", 2)), listOf("Gold", "Wet"), noMutations) { it }
        assertTrue(wet.enabled)
        assertEquals("No cleansers", CropCleansers.pickerState(emptyList(), listOf("Wet"), noMutations) { it }.label)
    }

    @Test fun `parseClearsMutationGroups reads the array and nothing else`() {
        val obj = JsonObject(mapOf("clearsMutationGroups" to JsonArray(listOf(JsonPrimitive("Hydro"), JsonPrimitive("Lunar")))))
        assertEquals(listOf("Hydro", "Lunar"), MgApi.parseClearsMutationGroups(obj))
        assertEquals(emptyList<String>(), MgApi.parseClearsMutationGroups(JsonObject(emptyMap())))
        assertEquals(emptyList<String>(), MgApi.parseClearsMutationGroups(JsonObject(mapOf("clearsMutationGroups" to JsonPrimitive("Hydro")))))
        assertEquals(emptyList<String>(), MgApi.parseClearsMutationGroups(null))
    }

    @Test fun `the Tools alert category leaves weather potions to their shop`() {
        fun entry(id: String, shops: List<String>) = MgApi.GameEntry(id = id, name = id, sprite = null, eligibleShops = shops)
        val items = listOf(entry("WetPotion", listOf("Rain")), entry("Shovel", listOf("Tool")), entry("OldTool", emptyList()))
        val tool = ShopAlertItems.forCategory("tool", emptyList(), emptyList(), items, emptyList()).map { it.id }
        assertEquals(listOf("Shovel", "OldTool"), tool)
        val rain = ShopAlertItems.forCategory("rain", emptyList(), emptyList(), items, emptyList()).map { it.id }
        assertEquals(listOf("WetPotion"), rain)
    }
}
