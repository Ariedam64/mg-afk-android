package com.mgafk.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Crop size is a whole number from 50 to 100, and that same number drives value, weight and
 * visuals. It used to be a fraction (1.0 up to the species' max) that the app had to convert
 * for display, which is where the "why didn't my +8% do 8%" confusion came from.
 *
 * These reproduce the game's own functions: clamp, then remap to a 1..maxSizeMultiplier factor.
 */
class CropSizeTest {

    private val delta = 1e-9

    @Test fun `size is a whole number clamped to the 50 to 100 range`() {
        assertEquals(50, CropSize.clamp(50.0))
        assertEquals(100, CropSize.clamp(100.0))
        assertEquals(68, CropSize.clamp(68.0))
        // Out of range on either side, the way the game guards it.
        assertEquals(50, CropSize.clamp(12.0))
        assertEquals(100, CropSize.clamp(140.0))
        // A fractional value rounds rather than truncating.
        assertEquals(69, CropSize.clamp(68.6))
        assertEquals(68, CropSize.clamp(68.4))
    }

    /** A missing or nonsensical value reads as the minimum, never as zero. */
    @Test fun `a size the game did not send reads as the minimum`() {
        assertEquals(50, CropSize.clamp(Double.NaN))
        assertEquals(50, CropSize.clamp(Double.POSITIVE_INFINITY))
        assertEquals(50, CropSize.clamp(0.0))
    }

    @Test fun `the smallest crop is worth its base price and the largest its full multiplier`() {
        assertEquals(1.0, CropSize.multiplier(50, maxSizeMultiplier = 3.0), delta)
        assertEquals(3.0, CropSize.multiplier(100, maxSizeMultiplier = 3.0), delta)
    }

    @Test fun `size scales the multiplier linearly between those two ends`() {
        assertEquals(2.0, CropSize.multiplier(75, maxSizeMultiplier = 3.0), delta)
        assertEquals(1.5, CropSize.multiplier(75, maxSizeMultiplier = 2.0), delta)
        // The old fractional scale of a size-68 crop with max 3: 1 + 2*(18/50).
        assertEquals(1.72, CropSize.multiplier(68, maxSizeMultiplier = 3.0), delta)
    }

    /** A species that cannot grow past its base size is worth its base price at any size. */
    @Test fun `a max multiplier of one leaves the value flat`() {
        assertEquals(1.0, CropSize.multiplier(50, maxSizeMultiplier = 1.0), delta)
        assertEquals(1.0, CropSize.multiplier(100, maxSizeMultiplier = 1.0), delta)
    }

    @Test fun `only a size of one hundred counts as maxed`() {
        assertEquals(true, CropSize.isMax(100))
        assertEquals(false, CropSize.isMax(99))
    }
}
