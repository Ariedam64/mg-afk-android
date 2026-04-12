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

    private val GROWTH_MUTATIONS = setOf("Gold", "Rainbow")

    private val FALLBACK_VALUES = mapOf(
        "Gold" to 25.0,
        "Rainbow" to 50.0,
        "Wet" to 2.0,
        "Chilled" to 2.0,
        "Frozen" to 6.0,
        "Thunderstruck" to 5.0,
        // Display names
        "Dawnlit" to 4.0,
        "Dawnbound" to 7.0,
        "Amberlit" to 6.0,
        "Amberbound" to 10.0,
        // Internal names (game state uses these)
        "Dawncharged" to 7.0,
        "Ambershine" to 6.0,
        "Ambercharged" to 10.0,
    )

    /**
     * Get mutation coinMultiplier from API data, falling back to hardcoded values.
     */
    private fun getMutationValue(mutation: String): Double {
        val apiEntry = MgApi.getMutations()[mutation]
        if (apiEntry != null) return apiEntry.coinMultiplier
        return FALLBACK_VALUES[mutation] ?: 1.0
    }

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
            if (mut in GROWTH_MUTATIONS) {
                val value = getMutationValue(mut)
                if (mut == "Rainbow") {
                    growth = value
                } else if (growth == 1.0) {
                    growth = value
                }
            } else {
                val value = getMutationValue(mut)
                if (value > 1.0) {
                    conditionSum += value
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
    /** Friends bonus multiplier: 1.0 for 1 player, +0.1 per extra, max 1.5 at 6. */
    fun friendsMultiplier(playerCount: Int): Double =
        (1.0 + (playerCount.coerceIn(1, 6) - 1) * 0.1)

    fun calculateCropSellPrice(
        species: String,
        targetScale: Double,
        mutations: List<String>,
        playerCount: Int = 1,
    ): Long? {
        val entry = MgApi.getPlants()[species] ?: return null
        val baseSellPrice = entry.baseSellPrice ?: return null
        if (baseSellPrice <= 0) return null

        val mutMultiplier = calculateMutationMultiplier(mutations)
        val friends = friendsMultiplier(playerCount)
        return (baseSellPrice * targetScale * mutMultiplier * friends).roundToLong()
    }

    /**
     * Calculate the sell price of a pet.
     * Formula: maturitySellPrice × (strength / maxStrength) × targetScale × coinMultiplier
     */
    fun calculatePetSellPrice(
        petSpecies: String,
        xp: Double,
        targetScale: Double,
        mutations: List<String>,
    ): Long? {
        val entry = MgApi.findPet(petSpecies) ?: return null
        val maturitySellPrice = entry.maturitySellPrice ?: return null
        if (maturitySellPrice <= 0) return null

        val maxScale = entry.maxScale ?: 1.0
        val hoursToMature = entry.hoursToMature ?: 1.0

        // Max strength (same logic as InventoryCard)
        val maxStrength = if (maxScale > 1.0 && targetScale > 1.0) {
            (80.0 + 20.0 * (targetScale - 1.0) / (maxScale - 1.0)).toInt().coerceIn(80, 100)
        } else 80

        // Current strength
        val xpRate = xp / (hoursToMature * 3600.0)
        val xpComponent = minOf((xpRate * 30.0).toInt(), 30)
        val baseStrength = (maxStrength - 30).coerceAtLeast(0)
        val strength = minOf(baseStrength + xpComponent, maxStrength).coerceAtLeast(0)

        if (maxStrength <= 0) return null

        // Coin multiplier from mutations
        val coinMultiplier = mutations.fold(1.0) { acc, mutation ->
            val mutEntry = MgApi.getMutations()[mutation]
            val mult = mutEntry?.coinMultiplier ?: 1.0
            if (mult > 0) acc * mult else acc
        }

        val raw = maturitySellPrice * (strength.toDouble() / maxStrength) * targetScale * coinMultiplier
        return if (raw.isFinite()) raw.roundToLong().coerceAtLeast(0) else null
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
