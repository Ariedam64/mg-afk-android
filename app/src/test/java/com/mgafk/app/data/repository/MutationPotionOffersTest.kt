package com.mgafk.app.data.repository

import com.mgafk.app.data.model.InventoryToolItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * BR-52: the plant detail also offers the mutation potions kept in the Tool Shack, says where each
 * one comes from with both quantities, puts the inventory first, and greys a Tool Shack potion out
 * when the inventory has no room to take it back.
 *
 * `MutationPotions.offers` is reached by reflection so this test compiles before it exists; once it
 * is written the test exercises it exactly as the garden screen will.
 */
class MutationPotionOffersTest {

    private data class Offer(
        val toolId: String,
        val mutation: String,
        val source: String,
        val inventoryQuantity: Int,
        val storageQuantity: Int,
        val canRetrieve: Boolean,
    )

    /** An empty catalogue, as when MG API did not answer: only the fallback table applies. */
    private val emptyCatalog: (String) -> MgApi.GameEntry? = { _ -> null }

    private fun offers(
        inventory: List<InventoryToolItem>,
        toolShack: List<InventoryToolItem>,
        bagCount: Int,
    ): List<Offer> {
        val cls = Class.forName("com.mgafk.app.data.repository.MutationPotions")
        val instance = cls.getField("INSTANCE").get(null)
        val method = cls.methods.firstOrNull { it.name == "offers" && it.parameterCount == 4 }
        if (method == null) {
            fail(
                "MutationPotions.offers(inventoryTools, toolShack, bagCount, catalog) does not exist: " +
                    "the potions kept in the Tool Shack are never offered on a plant"
            )
            throw IllegalStateException()
        }
        val result = method.invoke(instance, inventory, toolShack, bagCount, emptyCatalog) as List<*>
        return result.map { item ->
            val c = item!!.javaClass
            Offer(
                toolId = c.getMethod("getToolId").invoke(item) as String,
                mutation = c.getMethod("getMutation").invoke(item) as String,
                source = (c.getMethod("getSource").invoke(item) as Enum<*>).name,
                inventoryQuantity = c.getMethod("getInventoryQuantity").invoke(item) as Int,
                storageQuantity = c.getMethod("getStorageQuantity").invoke(item) as Int,
                canRetrieve = c.getMethod("getCanRetrieve").invoke(item) as Boolean,
            )
        }
    }

    /** Criterion 1: a potion only in the Tool Shack is offered, from STORAGE, and can be retrieved. */
    @Test fun `a potion kept only in the tool shack is offered from storage`() {
        val result = offers(
            inventory = emptyList(),
            toolShack = listOf(InventoryToolItem(toolId = "GoldPotion", quantity = 3)),
            bagCount = 10,
        )

        assertEquals(
            listOf(Offer("GoldPotion", "Gold", "STORAGE", inventoryQuantity = 0, storageQuantity = 3, canRetrieve = true)),
            result,
        )
    }

    /** Criterion 2: held and stored stacks of the same potion are one offer, from the inventory. */
    @Test fun `the inventory comes first when the potion is in both places`() {
        val result = offers(
            inventory = listOf(InventoryToolItem(toolId = "GoldPotion", quantity = 1)),
            toolShack = listOf(InventoryToolItem(toolId = "GoldPotion", quantity = 4)),
            bagCount = 10,
        )

        assertEquals(1, result.size)
        val offer = result.single()
        assertEquals("GoldPotion", offer.toolId)
        assertEquals("INVENTORY", offer.source)
        assertEquals(1, offer.inventoryQuantity)
        assertEquals(4, offer.storageQuantity)
    }

    /** Criterion 3: a full inventory cannot take a new stack back from the Tool Shack. */
    @Test fun `a full inventory cannot retrieve a potion kept only in the tool shack`() {
        val result = offers(
            inventory = emptyList(),
            toolShack = listOf(InventoryToolItem(toolId = "GoldPotion", quantity = 2)),
            bagCount = StorageCapacity.INVENTORY_LIMIT,
        )

        assertEquals(1, result.size)
        assertEquals("STORAGE", result.single().source)
        assertEquals(false, result.single().canRetrieve)
    }

    /** Criterion 3: a stack already held still merges, so a full inventory keeps the potion usable. */
    @Test fun `a full inventory holding the potion still offers it from the inventory`() {
        val result = offers(
            inventory = listOf(InventoryToolItem(toolId = "GoldPotion", quantity = 1)),
            toolShack = listOf(InventoryToolItem(toolId = "GoldPotion", quantity = 2)),
            bagCount = StorageCapacity.INVENTORY_LIMIT,
        )

        assertEquals(1, result.size)
        assertEquals("INVENTORY", result.single().source)
    }

    /** Criterion 4: tools without a granted mutation and empty stacks are not offered. */
    @Test fun `tool shack tools without a mutation or empty are not offered`() {
        val result = offers(
            inventory = emptyList(),
            toolShack = listOf(
                InventoryToolItem(toolId = "XPPotion", quantity = 2),
                InventoryToolItem(toolId = "CropCleanser", quantity = 5),
                InventoryToolItem(toolId = "RainbowPotion", quantity = 0),
            ),
            bagCount = 10,
        )

        assertTrue(result.isEmpty())
    }
}
