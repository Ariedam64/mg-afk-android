package com.mgafk.app.ui.screens.garden

import com.mgafk.app.data.repository.MutationPotions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * BR-53: a single crop shows one "Potion" button opening the same drop-down menu as a multi-slot
 * plant, through a picker shared by both dialogs. Its decision (button enabled, button label, menu
 * rows) lives in the pure `mutationPotionPickerState`.
 *
 * `mutationPotionPickerState` is reached by reflection so this test compiles before it exists; once
 * it is written the test exercises it exactly as the picker will.
 */
class MutationPotionPickerTest {

    private data class Item(val offer: Any?, val label: String, val enabled: Boolean)

    private data class State(val enabled: Boolean, val buttonLabel: String, val items: List<Item>)

    private val names: (String) -> String? = { id -> if (id == "GoldPotion") "Gold Potion" else null }

    private fun pickerState(potions: List<MutationPotions.Offer>): State {
        val cls = try {
            Class.forName("com.mgafk.app.ui.screens.garden.MutationPotionPickerKt")
        } catch (e: ClassNotFoundException) {
            null
        }
        val method = cls?.methods?.firstOrNull {
            it.name == "mutationPotionPickerState" && it.parameterCount == 2
        }
        if (method == null) {
            fail(
                "mutationPotionPickerState(potions, nameOf) does not exist in MutationPotionPicker.kt: " +
                    "a single crop has no shared Potion button and menu"
            )
            throw IllegalStateException()
        }
        val result = method.invoke(null, potions, names)!!
        val rc = result.javaClass
        val items = (rc.getMethod("getItems").invoke(result) as List<*>).map { item ->
            val c = item!!.javaClass
            Item(
                offer = c.getMethod("getOffer").invoke(item),
                label = c.getMethod("getLabel").invoke(item) as String,
                enabled = c.getMethod("getEnabled").invoke(item) as Boolean,
            )
        }
        return State(
            enabled = rc.getMethod("getEnabled").invoke(result) as Boolean,
            buttonLabel = rc.getMethod("getButtonLabel").invoke(result) as String,
            items = items,
        )
    }

    private val held = MutationPotions.Offer(
        toolId = "GoldPotion",
        mutation = "Gold",
        inventoryQuantity = 2,
        storageQuantity = 0,
        storageKey = null,
        canRetrieve = true,
    )

    private val storedFull = MutationPotions.Offer(
        toolId = "RainbowPotion",
        mutation = "Rainbow",
        inventoryQuantity = 0,
        storageQuantity = 3,
        storageKey = "shack-0",
        canRetrieve = false,
    )

    /** Criterion 1: no potion, the button is disabled, reads "No potion" and lists nothing. */
    @Test fun `no potion gives a disabled No potion button without rows`() {
        val state = pickerState(emptyList())

        assertEquals(false, state.enabled)
        assertEquals("No potion", state.buttonLabel)
        assertTrue(state.items.isEmpty())
    }

    /** Criteria 2, 3 and 4: rows keep the order, the greying, the labels and the original offers. */
    @Test fun `an inventory and a full tool shack potion give two rows in order`() {
        val state = pickerState(listOf(held, storedFull))

        assertEquals(true, state.enabled)
        assertEquals("Potion", state.buttonLabel)
        assertEquals(2, state.items.size)

        assertEquals(held, state.items[0].offer)
        assertEquals(true, state.items[0].enabled)
        assertEquals(mutationPotionLabel(held, "Gold Potion"), state.items[0].label)

        assertEquals(storedFull, state.items[1].offer)
        assertEquals(false, state.items[1].enabled)
        assertEquals(mutationPotionLabel(storedFull, "RainbowPotion"), state.items[1].label)
        assertTrue(state.items[1].label.contains("Inventory full — free a slot"))
    }
}
