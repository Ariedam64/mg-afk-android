package com.mgafk.app.data.repository

/**
 * The strength figure the pet tiles and pet detail dialogs show (inventory, storage, team
 * editor, hatch reveal), as "current/max" or just "max" once fully grown.
 *
 * This is the screens' own estimate, kept apart from [PriceCalculator.calculatePetStrength]
 * because the two disagree on edge cases: a species with a max scale at or below 1 reads as
 * 100 here and 80 there, and a zero maturation time reads as 0 here and as the ceiling there.
 */
object PetTileStrength {

    private const val XP_PER_HOUR = 3600.0
    private const val BASE_STR = 80
    private const val MAX_STR = 100
    private const val STR_GAINED = 30

    /** Strength ceiling (80-100) for [species] at size [scale], 80 when the species is unknown. */
    fun maxStr(species: String, scale: Double): Int = maxStr(MgApi.findPet(species)?.maxScale, scale)

    /** Strength ceiling (80-100) from a pet's [scale] against its species' [maxScale]. */
    fun maxStr(maxScale: Double?, scale: Double): Int {
        val ms = maxScale ?: return BASE_STR
        if (scale <= 1.0) return BASE_STR
        if (scale >= ms) return MAX_STR
        return (BASE_STR + 20 * (scale - 1.0) / (ms - 1.0)).toInt()
    }

    /** Current strength of a [species] pet with [xp], under its ceiling [max]. */
    fun curStr(species: String, xp: Double, max: Int): Int =
        curStr(MgApi.findPet(species)?.hoursToMature, xp, max)

    /** Current strength from [xp] against the species' [hoursToMature], under its ceiling [max]. */
    fun curStr(hoursToMature: Double?, xp: Double, max: Int): Int {
        val htm = hoursToMature ?: return max - STR_GAINED
        if (htm <= 0) return max
        val gained = minOf(STR_GAINED / htm * (xp / XP_PER_HOUR), STR_GAINED.toDouble())
        return ((max - STR_GAINED) + gained).toInt()
    }
}
