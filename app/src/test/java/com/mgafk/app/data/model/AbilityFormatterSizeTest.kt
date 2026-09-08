package com.mgafk.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Crop Size Boost adds a flat number of Size points now, not a percentage of an internal
 * fraction. The log parameter changed name with it: `scaleIncreasePercentage` became
 * `sizeIncrease`, so the old reader would render "+?%" on every boost.
 */
class AbilityFormatterSizeTest {

    private fun log(action: String, params: Map<String, String>) =
        AbilityLog(action = action, params = params)

    @Test fun `a size boost reports the points it added`() {
        val text = AbilityFormatter.format(
            log("ProduceScaleBoostII", mapOf("sizeIncrease" to "7", "numPlantsAffected" to "3"))
        )
        assertEquals("Boosted 3 crops size by +7", text)
    }

    @Test fun `a single crop reads in the singular`() {
        val text = AbilityFormatter.format(
            log("ProduceScaleBoost", mapOf("sizeIncrease" to "4", "numPlantsAffected" to "1"))
        )
        assertEquals("Boosted 1 crop size by +4", text)
    }

    @Test fun `the snow boost is formatted the same way`() {
        val text = AbilityFormatter.format(
            log("SnowyCropSizeBoost", mapOf("sizeIncrease" to "8", "numPlantsAffected" to "2"))
        )
        assertEquals("Boosted 2 crops size by +8", text)
    }

    /** The game sends a whole number; a decimal one must not leak a ".0" into the log. */
    @Test fun `a decimal amount is shown as a whole number`() {
        val text = AbilityFormatter.format(
            log("ProduceScaleBoostIII", mapOf("sizeIncrease" to "9.0", "numPlantsAffected" to "1"))
        )
        assertEquals("Boosted 1 crop size by +9", text)
    }
}
