package com.mgafk.app.data.repository

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Crop sell price calculator.
 * Port of Gemini's modules/calculators/logic/crop.ts + mutation.ts
 *
 * Formula: baseSellPrice × targetScale × mutationMultiplier
 */
object PriceCalculator {

    // ── Mutation multipliers (fallback if API unavailable) ──
    // Source: game source mutationsDex.ts

    private val GROWTH_MUTATIONS = mapOf(
        "Gold" to 25.0,
        "Rainbow" to 50.0,
    )

    private val CONDITION_MUTATIONS = mapOf(
        "Wet" to 2.0,
        "Chilled" to 2.0,
        "Frozen" to 10.0,
        "Dawnlit" to 2.0,
        "Dawnbound" to 3.0,
        "Amberlit" to 5.0,
        "Amberbound" to 6.0,
    )

    /**
     * Calculate the mutation multiplier for a list of mutations.
     *
     * - Growth mutations (Gold 25x, Rainbow 50x) are exclusive (Rainbow wins)
     * - Conditions (weather + time) stack: growth × (1 + Σvalues - count)
     */
    fun calculateMutationMultiplier(mutations: List<String>): Double {
        var growth = 1.0
        var conditionSum = 0.0
        var conditionCount = 0

        for (mut in mutations) {
            val growthVal = GROWTH_MUTATIONS[mut]
            if (growthVal != null) {
                if (mut == "Rainbow") {
                    growth = growthVal
                } else if (growth == 1.0) {
                    growth = growthVal
                }
            } else {
                val condVal = CONDITION_MUTATIONS[mut]
                if (condVal != null && condVal > 1.0) {
                    conditionSum += condVal
                    conditionCount++
                }
            }
        }

        return growth * (1.0 + conditionSum - conditionCount)
    }

    /**
     * Calculate the sell price of a crop.
     *
     * @param species Crop species id (e.g. "Carrot")
     * @param targetScale Scale value from game state
     * @param mutations List of mutation names
     * @return Sell price in coins, or null if species data unavailable
     */
    fun calculateCropSellPrice(
        species: String,
        targetScale: Double,
        mutations: List<String>,
    ): Long? {
        val entry = MgApi.getPlants()[species] ?: return null
        val baseSellPrice = entry.baseSellPrice ?: return null
        if (baseSellPrice <= 0) return null

        val mutMultiplier = calculateMutationMultiplier(mutations)
        return (baseSellPrice * targetScale * mutMultiplier).roundToLong()
    }

    /**
     * Format a coin value with suffix (K, M, B, T).
     */
    fun formatPrice(coins: Long): String {
        val a = abs(coins)
        val sign = if (coins < 0) "-" else ""
        return when {
            a >= 1_000_000_000_000 -> "${sign}${fmtNum(a / 1_000_000_000_000.0)}T"
            a >= 1_000_000_000 -> "${sign}${fmtNum(a / 1_000_000_000.0)}B"
            a >= 1_000_000 -> "${sign}${fmtNum(a / 1_000_000.0)}M"
            a >= 1_000 -> "${sign}${fmtNum(a / 1_000.0)}K"
            else -> "${sign}$a"
        }
    }

    private fun fmtNum(value: Double): String {
        return if (value >= 100) value.toLong().toString()
        else String.format("%.1f", value).removeSuffix(".0")
    }
}
