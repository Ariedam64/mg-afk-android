package com.mgafk.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The game refuses an XP Potion on a pet that is already fully grown: the reducer bails out
 * before consuming anything. The app has to know the same rule, or it offers a button that
 * burns a Legendary item for nothing.
 *
 * A pet is maxed once its xp covers its species' full maturation time. That threshold is the
 * same whatever the pet's size, because growing bigger lifts the strength ceiling and the
 * starting strength by the same 30 points.
 */
class PetStrengthTest {

    // A Worm matures in 12 hours and grows to twice its size.
    private val hoursToMature = 12.0
    private val maxScale = 2.0
    private val fullyGrownXp = hoursToMature * 3600.0

    private fun isMax(xp: Double, targetScale: Double = 1.0) =
        PriceCalculator.isPetMaxStrength(xp, targetScale, maxScale, hoursToMature)

    @Test fun `a newly hatched pet is not fully grown`() {
        assertFalse(isMax(0.0))
    }

    @Test fun `a pet is fully grown once its xp covers its maturation time`() {
        assertTrue(isMax(fullyGrownXp))
    }

    @Test fun `one second short of maturity still leaves room for a potion`() {
        assertFalse(isMax(fullyGrownXp - 1.0))
    }

    @Test fun `extra xp past maturity keeps the pet fully grown`() {
        assertTrue(isMax(fullyGrownXp * 3))
    }

    /** Size lifts the ceiling and the floor together, so it never moves the finish line. */
    @Test fun `size does not change when a pet becomes fully grown`() {
        assertFalse(isMax(fullyGrownXp - 1.0, targetScale = maxScale))
        assertTrue(isMax(fullyGrownXp, targetScale = maxScale))
    }

    /** Mirrors calculatePetStrength, which reports the ceiling when there is no maturation time. */
    @Test fun `a species without a maturation time counts as fully grown`() {
        assertTrue(PriceCalculator.isPetMaxStrength(0.0, 1.0, maxScale, hoursToMature = 0.0))
    }

    /**
     * The guard exists to agree with the strength the app already displays, so it is checked
     * against that rather than against a second copy of the formula.
     */
    @Test fun `it agrees with the strength calculation it guards`() {
        val xpValues = listOf(0.0, 1_000.0, fullyGrownXp - 1.0, fullyGrownXp, fullyGrownXp * 2)
        val scales = listOf(1.0, 1.5, 2.0)
        for (xp in xpValues) {
            for (scale in scales) {
                val strength = PriceCalculator.calculatePetStrength(xp, scale, maxScale, hoursToMature)
                val ceiling = PriceCalculator.calculateMaxStrength(scale, maxScale)
                assertEquals("xp=$xp scale=$scale", strength >= ceiling, isMax(xp, scale))
            }
        }
    }
}
