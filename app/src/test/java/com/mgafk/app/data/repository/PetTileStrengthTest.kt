package com.mgafk.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The strength the pet tiles show: a ceiling from 80 to 100 set by size, and a current value
 * that climbs 30 points over the species' maturation time.
 */
class PetTileStrengthTest {

    // A Worm matures in 12 hours and grows to twice its size.
    private val hoursToMature = 12.0
    private val maxScale = 2.0

    @Test fun `the ceiling runs from 80 at base size to 100 at full size`() {
        assertEquals(80, PetTileStrength.maxStr(maxScale, 1.0))
        assertEquals(90, PetTileStrength.maxStr(maxScale, 1.5))
        assertEquals(100, PetTileStrength.maxStr(maxScale, 2.0))
    }

    @Test fun `the ceiling stays within 80 to 100 outside the size range`() {
        assertEquals(80, PetTileStrength.maxStr(maxScale, 0.5))
        assertEquals(100, PetTileStrength.maxStr(maxScale, 3.0))
    }

    @Test fun `a species without a max scale reads as the base ceiling`() {
        assertEquals(80, PetTileStrength.maxStr(null, 1.5))
    }

    @Test fun `the ceiling truncates rather than rounds`() {
        // 80 + 20 * 0.5 / 2 = 85, and 80 + 20 * 0.99 / 1 = 99.8 shows as 99.
        assertEquals(85, PetTileStrength.maxStr(3.0, 1.5))
        assertEquals(99, PetTileStrength.maxStr(maxScale, 1.99))
    }

    @Test fun `a newly hatched pet starts 30 below its ceiling`() {
        assertEquals(60, PetTileStrength.curStr(hoursToMature, 0.0, 90))
    }

    @Test fun `strength climbs with xp and stops at the ceiling`() {
        assertEquals(75, PetTileStrength.curStr(hoursToMature, 6 * 3600.0, 90))
        assertEquals(90, PetTileStrength.curStr(hoursToMature, 12 * 3600.0, 90))
        assertEquals(90, PetTileStrength.curStr(hoursToMature, 48 * 3600.0, 90))
    }

    @Test fun `a species without a maturation time reads as just hatched`() {
        assertEquals(50, PetTileStrength.curStr(null, 99_999.0, 80))
    }

    /** For ordinary pets the tiles and the price calculator must show the same number. */
    @Test fun `it agrees with the price calculator for ordinary pets`() {
        val fullyGrownXp = hoursToMature * 3600.0
        for (xp in listOf(0.0, 1_000.0, fullyGrownXp / 2, fullyGrownXp - 1.0, fullyGrownXp, fullyGrownXp * 2)) {
            for (scale in listOf(1.0, 1.25, 1.5, 1.99, 2.0)) {
                val max = PetTileStrength.maxStr(maxScale, scale)
                assertEquals("max scale=$scale", PriceCalculator.calculateMaxStrength(scale, maxScale), max)
                assertEquals(
                    "xp=$xp scale=$scale",
                    PriceCalculator.calculatePetStrength(xp, scale, maxScale, hoursToMature),
                    PetTileStrength.curStr(hoursToMature, xp, max),
                )
            }
        }
    }
}
