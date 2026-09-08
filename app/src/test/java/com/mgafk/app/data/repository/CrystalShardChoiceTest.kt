package com.mgafk.app.data.repository

import com.mgafk.app.data.model.CrystalShard
import com.mgafk.app.data.model.CrystalType
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.model.OwnedShard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which shard to spend, when the player owns several.
 *
 * Shards bought from a shop sit in a stack and are all worth four hours. One picked back up
 * keeps the time it had left and is an item of its own, so a player can hold a 20 minute
 * fragment next to a full stack. Since fusing consumes a shard whole and the twelve hour
 * ceiling throws away the excess, picking the wrong one burns a Legendary item for nothing.
 */
class CrystalShardChoiceTest {

    private val type = CrystalType.XP

    private fun stacked(quantity: Int) =
        InventoryToolItem(toolId = type.toolId, quantity = quantity)

    private fun used(id: String, seconds: Int) =
        InventoryToolItem(toolId = type.toolId, quantity = 1, id = id, remainingActiveSeconds = seconds)

    // ── Reading the inventory ──

    @Test fun `a stack is worth a fresh shard`() {
        val shards = Crystals.ownedShards(type, listOf(stacked(3)))

        assertEquals(1, shards.size)
        assertEquals(Crystals.FRESH_SHARD_SECONDS, shards.single().seconds)
        assertTrue(shards.single().fromStack)
        assertEquals(CrystalShard.Stacked(type.toolId), shards.single().ref)
    }

    @Test fun `a picked up shard keeps its own time and travels by id`() {
        val shards = Crystals.ownedShards(type, listOf(used("frag-1", 1_200)))

        assertEquals(1_200, shards.single().seconds)
        assertEquals(false, shards.single().fromStack)
        assertEquals(CrystalShard.Used("frag-1"), shards.single().ref)
    }

    @Test fun `shards of other kinds are left out`() {
        val tools = listOf(stacked(1), InventoryToolItem(toolId = "RainWardShard", quantity = 5))

        assertEquals(1, Crystals.ownedShards(type, tools).size)
    }

    @Test fun `an empty stack is not a shard`() {
        assertTrue(Crystals.ownedShards(type, listOf(stacked(0))).isEmpty())
    }

    // ── Planting ──

    /** Fragments are worth least and clutter the inventory, so they go first. */
    @Test fun `planting spends the smallest fragment before a full shard`() {
        val shards = Crystals.ownedShards(type, listOf(stacked(5), used("frag-1", 3_600), used("frag-2", 900)))

        assertEquals(CrystalShard.Used("frag-2"), Crystals.chooseForPlace(shards)?.ref)
    }

    @Test fun `planting falls back to the stack`() {
        assertEquals(
            CrystalShard.Stacked(type.toolId),
            Crystals.chooseForPlace(Crystals.ownedShards(type, listOf(stacked(2))))?.ref,
        )
    }

    @Test fun `nothing to plant when nothing is owned`() {
        assertNull(Crystals.chooseForPlace(emptyList()))
    }

    // ── Fusing ──

    /**
     * The rule is to waste as little as possible, and to gain as much as possible among the
     * shards that waste the same. Here the crystal has room for 8200s: a full shard would throw
     * away 6200, the fragment fits whole.
     */
    @Test fun `fusing prefers a fragment that fits over wasting a full shard`() {
        val shards = Crystals.ownedShards(type, listOf(stacked(4), used("frag-1", 5_000)))

        val chosen = Crystals.chooseForFuse(shards, targetSeconds = 35_000)

        assertEquals(CrystalShard.Used("frag-1"), chosen?.ref)
    }

    /** When no shard fits whole, the one that spills the least wins. */
    @Test fun `fusing takes the smallest overflow`() {
        val shards = Crystals.ownedShards(type, listOf(stacked(4), used("big", 30_000)))

        val chosen = Crystals.chooseForFuse(shards, targetSeconds = 35_000)

        assertTrue("the full shard spills less than the big fragment", chosen!!.fromStack)
    }

    /** Among shards that waste nothing, the one buying the most time wins. */
    @Test fun `fusing takes the most time when nothing is wasted either way`() {
        val shards = Crystals.ownedShards(type, listOf(stacked(4), used("frag-1", 5_000)))

        val chosen = Crystals.chooseForFuse(shards, targetSeconds = 14_400)

        assertEquals(Crystals.FRESH_SHARD_SECONDS, chosen?.seconds)
    }

    /** A crystal already at the ceiling gains nothing, so no shard is worth spending. */
    @Test fun `nothing is fused into a full crystal`() {
        val shards = Crystals.ownedShards(type, listOf(stacked(4)))

        assertNull(Crystals.chooseForFuse(shards, targetSeconds = Crystals.MAX_ACTIVE_SECONDS))
    }
}
