package com.mgafk.app.data.repository

import com.mgafk.app.data.model.CrystalType
import com.mgafk.app.data.model.GardenTileType
import com.mgafk.app.data.model.PlacedCrystal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules a crystal follows, ported from the game's own reducer: a fresh shard is worth four
 * hours, fusing tops a crystal up to twelve, and a garden holds four of each ward but only one
 * of each pet crystal.
 */
class CrystalsTest {

    private var nextTile = 0

    private fun crystal(type: CrystalType, seconds: Int = Crystals.FRESH_SHARD_SECONDS) =
        PlacedCrystal(
            type = type,
            tileType = GardenTileType.Dirt,
            localTileIndex = nextTile++,
            remainingSeconds = seconds,
        )

    // ── Fusing ──

    @Test fun `fusing two fresh shards doubles the time`() {
        assertEquals(
            Crystals.FRESH_SHARD_SECONDS,
            Crystals.mergeGainSeconds(Crystals.FRESH_SHARD_SECONDS, Crystals.FRESH_SHARD_SECONDS),
        )
    }

    @Test fun `a fuse that lands exactly on the cap gives its full value`() {
        assertEquals(14_400, Crystals.mergeGainSeconds(28_800, 14_400))
    }

    /**
     * The source shard is consumed whole whatever happens, so the part that will not fit under
     * the cap is simply lost. Saying so is the difference between an informed fuse and a
     * wasted Legendary shard.
     */
    @Test fun `a fuse over the cap only gains what fits`() {
        assertEquals(8_200, Crystals.mergeGainSeconds(35_000, 14_400))
        assertEquals(6_200, Crystals.wastedSeconds(35_000, 14_400))
    }

    @Test fun `nothing is wasted when the whole shard fits`() {
        assertEquals(0, Crystals.wastedSeconds(14_400, 14_400))
    }

    @Test fun `a crystal at the cap gains nothing at all`() {
        assertEquals(0, Crystals.mergeGainSeconds(Crystals.MAX_ACTIVE_SECONDS, 14_400))
        assertEquals(14_400, Crystals.wastedSeconds(Crystals.MAX_ACTIVE_SECONDS, 14_400))
    }

    /**
     * The server refuses a gain bigger than the crystal's real headroom and accepts a smaller
     * one, so an app whose reading is out of date must err towards the older, larger value.
     * This is why a fuse measures against the last figure the server reported rather than the
     * one ticking down on screen: reading high can only ask for less.
     */
    @Test fun `an older higher reading never asks for more`() {
        val source = Crystals.FRESH_SHARD_SECONDS
        val realValues = listOf(0, 5_000, 28_800, 35_000, Crystals.MAX_ACTIVE_SECONDS)
        val drifts = listOf(0, 60, 600, 3_600)
        for (real in realValues) {
            for (drift in drifts) {
                val older = (real + drift).coerceAtMost(Crystals.MAX_ACTIVE_SECONDS)
                assertTrue(
                    "older=$older real=$real",
                    Crystals.mergeGainSeconds(older, source) <= Crystals.mergeGainSeconds(real, source),
                )
            }
        }
    }

    @Test fun `only a crystal at the cap counts as full`() {
        assertTrue(Crystals.isAtMaxLifespan(Crystals.MAX_ACTIVE_SECONDS))
        assertTrue(Crystals.isAtMaxLifespan(Crystals.MAX_ACTIVE_SECONDS + 1))
        assertFalse(Crystals.isAtMaxLifespan(Crystals.MAX_ACTIVE_SECONDS - 1))
    }

    // ── Per-garden limits ──

    @Test fun `a garden holds four wards of one kind`() {
        val three = List(3) { crystal(CrystalType.RainWard) }
        assertTrue(Crystals.canPlace(CrystalType.RainWard, three))
        assertFalse(Crystals.canPlace(CrystalType.RainWard, three + crystal(CrystalType.RainWard)))
    }

    @Test fun `ward kinds are counted apart`() {
        val rainAtLimit = List(4) { crystal(CrystalType.RainWard) }
        assertFalse(Crystals.canPlace(CrystalType.RainWard, rainAtLimit))
        assertTrue(Crystals.canPlace(CrystalType.SnowWard, rainAtLimit))
    }

    @Test fun `a garden holds a single pet crystal of each kind`() {
        assertTrue(Crystals.canPlace(CrystalType.XP, emptyList()))
        assertFalse(Crystals.canPlace(CrystalType.XP, listOf(crystal(CrystalType.XP))))
        assertTrue(Crystals.canPlace(CrystalType.Hunger, listOf(crystal(CrystalType.XP))))
    }

    /** An expired crystal still occupies its tile but no longer counts against the limit. */
    @Test fun `an expired crystal frees up its slot`() {
        val expired = List(4) { crystal(CrystalType.RainWard, seconds = 0) }
        assertTrue(Crystals.canPlace(CrystalType.RainWard, expired))
    }

    // ── Effects ──

    @Test fun `a garden without crystals changes nothing`() {
        val effects = Crystals.effects(emptyList())
        assertEquals(1.0, effects.hungerRateMultiplier, 0.0)
        assertEquals(1.0, effects.xpRateMultiplier, 0.0)
        assertEquals(0, effects.strengthBonus)
        assertFalse(effects.hasAny)
    }

    @Test fun `each pet crystal contributes its own effect`() {
        val effects = Crystals.effects(
            listOf(crystal(CrystalType.Hunger), crystal(CrystalType.XP), crystal(CrystalType.Strength)),
        )
        assertEquals(0.9, effects.hungerRateMultiplier, 1e-9)
        assertEquals(1.1, effects.xpRateMultiplier, 1e-9)
        assertEquals(10, effects.strengthBonus)
        assertTrue(effects.hasAny)
    }

    /** The effect is a switch, not a stack: a second crystal of a kind adds nothing. */
    @Test fun `effects do not stack`() {
        val effects = Crystals.effects(listOf(crystal(CrystalType.XP), crystal(CrystalType.XP)))
        assertEquals(1.1, effects.xpRateMultiplier, 1e-9)
    }

    @Test fun `an expired crystal has no effect`() {
        val effects = Crystals.effects(listOf(crystal(CrystalType.XP, seconds = 0)))
        assertEquals(1.0, effects.xpRateMultiplier, 0.0)
        assertFalse(effects.hasAny)
    }

    @Test fun `a ward has no effect on pets`() {
        assertFalse(Crystals.effects(listOf(crystal(CrystalType.RainWard))).hasAny)
    }
}
