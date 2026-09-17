package com.mgafk.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The storage commands name an item by one key, and for tools the game picks it per item: a
 * stacked tool answers to its toolId, while one the game tracks individually answers to its own
 * id. A crystal shard picked back up is the second kind, which is why sending its toolId moved
 * nothing at all.
 */
class InventoryToolKeyTest {

    private val stacked = InventoryToolItem(toolId = "StrengthShard", quantity = 4)

    private val pickedUp = InventoryToolItem(
        toolId = "StrengthShard",
        quantity = 1,
        id = "shard-7f2c",
        remainingActiveSeconds = 9_000,
    )

    @Test fun `a stacked tool is named by its tool id`() {
        assertEquals("StrengthShard", stacked.storageKey)
    }

    @Test fun `a tool the game tracks on its own is named by its id`() {
        assertEquals("shard-7f2c", pickedUp.storageKey)
    }

    /** What tells the two apart, and what decides whether a stack can absorb it. */
    @Test fun `only a tool without its own id stacks`() {
        assertTrue(stacked.isStackable)
        assertFalse(pickedUp.isStackable)
    }

    /**
     * A shard is unique from the moment it carries time, even at full duration: the id is what
     * makes it its own item, not how much is left on it.
     */
    @Test fun `a full but individually tracked shard is still named by its id`() {
        val fresh = InventoryToolItem(
            toolId = "StrengthShard",
            quantity = 1,
            id = "shard-new",
            remainingActiveSeconds = 14_400,
        )

        assertEquals("shard-new", fresh.storageKey)
        assertFalse(fresh.isStackable)
    }
}
