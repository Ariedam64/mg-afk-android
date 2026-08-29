package com.mgafk.app.data.repository

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
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

    // ── Dust value (sell price in Magic Dust) ──
    // Port of Gemini's modules/calculators/logic/pet.ts:calculatePetDustValue
    // Source: function `mg` in QuinoaView game bundle.

    private val RARITY_DUST_MULT = mapOf(
        "Common" to 1.0,
        "Uncommon" to 2.0,
        "Rare" to 5.0,
        "Legendary" to 10.0,
        "Mythic" to 50.0,
        "Mythical" to 50.0,
        "Divine" to 50.0,
        "Celestial" to 50.0,
    )

    private fun hatchChanceDustMult(chancePct: Double): Double = when {
        chancePct >= 51 -> 1.0
        chancePct >= 11 -> 2.0
        else -> 5.0
    }

    private fun mutationDustMult(mutations: List<String>): Double = when {
        "Rainbow" in mutations -> 50.0
        "Gold" in mutations -> 25.0
        else -> 1.0
    }

    /**
     * Calculate the Magic Dust sell value of a pet.
     * Formula: floor(100 × rarityMult × hatchMult × mutationMult × scaleMult)
     *   rarityMult   - from pet species rarity
     *   hatchMult    - from pet's hatch chance in its source egg
     *   mutationMult - Rainbow=50, Gold=25, else 1
     *   scaleMult    - (currentStrength × targetScale) / maxStrength
     */
    fun calculatePetDustValue(
        petSpecies: String,
        sourceEggId: String,
        xp: Double,
        targetScale: Double,
        mutations: List<String>,
    ): Long? {
        val petEntry = MgApi.findPet(petSpecies) ?: return null
        val maxScale = petEntry.maxScale ?: 1.0
        val hoursToMature = petEntry.hoursToMature ?: 1.0

        val rarityMult = petEntry.rarity?.let { RARITY_DUST_MULT[it] } ?: 1.0

        val weights = if (sourceEggId.isNotEmpty())
            MgApi.getEggs()[sourceEggId]?.faunaSpawnWeights.orEmpty()
        else emptyMap()
        val chancePct = if (weights.isNotEmpty()) {
            val total = weights.values.sum()
            val thisWeight = weights[petSpecies] ?: 0.0
            if (total > 0) (thisWeight / total) * 100.0 else 100.0
        } else 100.0
        val chanceMult = hatchChanceDustMult(chancePct)

        val mutMult = mutationDustMult(mutations)

        val maxStrength = if (maxScale > 1.0 && targetScale > 1.0) {
            (80.0 + 20.0 * (targetScale - 1.0) / (maxScale - 1.0)).toInt().coerceIn(80, 100)
        } else 80
        if (maxStrength <= 0) return 0

        val xpRate = xp / (hoursToMature * 3600.0)
        val xpComponent = minOf((xpRate * 30.0).toInt(), 30)
        val baseStrength = (maxStrength - 30).coerceAtLeast(0)
        val currentStrength = minOf(baseStrength + xpComponent, maxStrength).coerceAtLeast(0)

        val scaleMult = (currentStrength * targetScale) / maxStrength
        val raw = 100.0 * rarityMult * chanceMult * mutMult * scaleMult
        return if (raw.isFinite()) floor(raw).toLong().coerceAtLeast(0) else null
    }

    // ── Storage upgrade tiers (PetHutch, SeedSilo, DecorShed, ToolShack) ──
    // Each decor's `upgrades` list (from `/data/decors`) is a chain of tiers keyed by
    // capacity, e.g. {fromCapacitySlots:10, toCapacitySlots:15, dustCost}. "Level" here
    // is just how many tiers of that chain have been applied (0 = base capacity).

    const val HUTCH_BASE_CAPACITY = 10
    const val SILO_BASE_CAPACITY = 10
    const val DECOR_SHED_BASE_CAPACITY = 10
    const val TOOL_SHACK_BASE_CAPACITY = 10

    /** Next upgrade tier for a leveled storage (PetHutch, SeedSilo, DecorShed, ToolShack). */
    data class StorageUpgradeInfo(
        val targetLevel: Int,
        val dustCost: Long,
        val capacityAfter: Int,
    )

    private fun storageUpgrades(storageId: String): List<MgApi.DecorUpgrade> =
        MgApi.getDecors()[storageId]?.upgrades ?: emptyList()

    private fun storageBaseCapacity(storageId: String): Int = when (storageId) {
        "PetHutch" -> HUTCH_BASE_CAPACITY
        "SeedSilo" -> SILO_BASE_CAPACITY
        "DecorShed" -> DECOR_SHED_BASE_CAPACITY
        "ToolShack" -> TOOL_SHACK_BASE_CAPACITY
        else -> 0
    }

    /** Total number of upgrade tiers available for this storage (i.e. max level). */
    fun storageMaxLevel(storageId: String): Int = storageUpgrades(storageId).size

    /** Capacity after [level] tiers have been applied (0 = base, untouched capacity). */
    fun storageCapacity(storageId: String, level: Int): Int {
        val tiers = storageUpgrades(storageId)
        if (level <= 0) return storageBaseCapacity(storageId)
        return tiers.getOrNull(level - 1)?.toCapacitySlots
            ?: tiers.lastOrNull()?.toCapacitySlots
            ?: storageBaseCapacity(storageId)
    }

    /**
     * Upgrade level matching a capacity reported by the game ("capacitySlots"):
     * how many tiers have already been completed to reach that capacity.
     */
    fun storageLevelForCapacity(storageId: String, capacitySlots: Int): Int =
        storageUpgrades(storageId).count { it.toCapacitySlots <= capacitySlots }

    /** Info about the next upgrade tier for this storage, or null if maxed. */
    fun getNextStorageUpgrade(storageId: String, capacityLevel: Int): StorageUpgradeInfo? {
        val tier = storageUpgrades(storageId).getOrNull(capacityLevel) ?: return null
        return StorageUpgradeInfo(
            targetLevel = capacityLevel + 1,
            dustCost = tier.dustCost,
            capacityAfter = tier.toCapacitySlots,
        )
    }

    fun calculateHutchCapacity(capacityLevel: Int): Int = storageCapacity("PetHutch", capacityLevel)
    fun calculateSiloCapacity(capacityLevel: Int): Int = storageCapacity("SeedSilo", capacityLevel)
    fun calculateDecorShedCapacity(capacityLevel: Int): Int = storageCapacity("DecorShed", capacityLevel)
    fun calculateToolShackCapacity(capacityLevel: Int): Int = storageCapacity("ToolShack", capacityLevel)

    fun hutchLevelForCapacity(capacitySlots: Int): Int = storageLevelForCapacity("PetHutch", capacitySlots)
    fun siloLevelForCapacity(capacitySlots: Int): Int = storageLevelForCapacity("SeedSilo", capacitySlots)
    fun decorShedLevelForCapacity(capacitySlots: Int): Int = storageLevelForCapacity("DecorShed", capacitySlots)
    fun toolShackLevelForCapacity(capacitySlots: Int): Int = storageLevelForCapacity("ToolShack", capacitySlots)

    fun getNextHutchUpgrade(capacityLevel: Int): StorageUpgradeInfo? = getNextStorageUpgrade("PetHutch", capacityLevel)
    fun getNextSiloUpgrade(capacityLevel: Int): StorageUpgradeInfo? = getNextStorageUpgrade("SeedSilo", capacityLevel)
    fun getNextDecorShedUpgrade(capacityLevel: Int): StorageUpgradeInfo? = getNextStorageUpgrade("DecorShed", capacityLevel)
    fun getNextToolShackUpgrade(capacityLevel: Int): StorageUpgradeInfo? = getNextStorageUpgrade("ToolShack", capacityLevel)

    /**
     * Full number with thousand separators (e.g. 1,234,567). Use in detail popups
     * where there's enough room - no rounding, no K/M/B suffix.
     */
    fun formatFull(value: Long): String {
        val sign = if (value < 0) "-" else ""
        val abs = if (value < 0) -value else value
        return "$sign${String.format(Locale.US, "%,d", abs)}"
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
