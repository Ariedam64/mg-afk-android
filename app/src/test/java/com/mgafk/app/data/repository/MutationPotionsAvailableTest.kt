package com.mgafk.app.data.repository

import com.mgafk.app.data.model.InventoryToolItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * BR-51: the plant detail only offers the mutation potions the player actually holds, each with
 * the mutation it grants. A potion without a granted mutation (XPPotion), the crop cleanser and
 * an empty stack must not be offered, and two stacks of the same potion count as one.
 *
 * `MutationPotions` is reached by reflection so this test compiles before it exists; once it is
 * written the test exercises it exactly as the dialogs will.
 */
class MutationPotionsAvailableTest {

    private data class Offer(val toolId: String, val mutation: String, val quantity: Int)

    /** An empty catalogue, as when MG API did not answer: only the fallback table applies. */
    private val emptyCatalog: (String) -> MgApi.GameEntry? = { _ -> null }

    private fun available(tools: List<InventoryToolItem>): List<Offer> {
        val cls = try {
            Class.forName("com.mgafk.app.data.repository.MutationPotions")
        } catch (e: ClassNotFoundException) {
            fail("MutationPotions does not exist: no list of mutation potions to offer on a plant")
            throw e
        }
        val instance = cls.getField("INSTANCE").get(null)
        val method = cls.methods.firstOrNull { it.name == "available" && it.parameterCount == 2 }
        if (method == null) {
            fail("MutationPotions.available(tools, catalog) does not exist")
            throw IllegalStateException()
        }
        val result = method.invoke(instance, tools, emptyCatalog) as List<*>
        return result.map { item ->
            val c = item!!.javaClass
            Offer(
                toolId = c.getMethod("getToolId").invoke(item) as String,
                mutation = c.getMethod("getMutation").invoke(item) as String,
                quantity = c.getMethod("getQuantity").invoke(item) as Int,
            )
        }
    }

    @Test fun `only held potions that grant a mutation are offered`() {
        val offers = available(
            listOf(
                InventoryToolItem(toolId = "WetPotion", quantity = 3),
                InventoryToolItem(toolId = "XPPotion", quantity = 1),
                InventoryToolItem(toolId = "CropCleanser", quantity = 2),
                InventoryToolItem(toolId = "GoldPotion", quantity = 0),
            )
        )

        assertEquals(listOf(Offer("WetPotion", "Wet", 3)), offers)
    }

    /** The Amberlit potion grants Ambershine, not "Amberlit": a name-based guess would be wrong. */
    @Test fun `the amberlit potion grants ambershine`() {
        val offers = available(listOf(InventoryToolItem(toolId = "AmberlitPotion", quantity = 1)))

        assertEquals(listOf(Offer("AmberlitPotion", "Ambershine", 1)), offers)
    }

    @Test fun `two stacks of the same potion are one offer with the summed quantity`() {
        val offers = available(
            listOf(
                InventoryToolItem(toolId = "GoldPotion", quantity = 2),
                InventoryToolItem(toolId = "GoldPotion", quantity = 5),
            )
        )

        assertEquals(listOf(Offer("GoldPotion", "Gold", 7)), offers)
    }

    /** No potion means no control: the dialogs hide or disable it on an empty list. */
    @Test fun `an inventory without mutation potions offers nothing`() {
        val offers = available(
            listOf(
                InventoryToolItem(toolId = "CropCleanser", quantity = 4),
                InventoryToolItem(toolId = "ReplenishPotion", quantity = 1),
            )
        )

        assertTrue(offers.isEmpty())
    }
}
