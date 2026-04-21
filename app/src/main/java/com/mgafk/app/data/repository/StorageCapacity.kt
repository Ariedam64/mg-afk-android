package com.mgafk.app.data.repository

/**
 * Storage capacity rules ported from QuinoaView (function returning capacity per
 * storage id). Values are baked into the game and not exposed via /data, so we
 * mirror the same constants here.
 *
 *   SeedSilo     → 25 distinct species
 *   DecorShed    → 25 distinct decor ids
 *   FeedingTrough→ 9
 *   PetHutch     → 25 + sum(PetHutch.upgrades.capacityBonus where target ≤ level)
 *   Inventory    → 100 items total (stackable items merge with existing slots)
 */
object StorageCapacity {

    const val INVENTORY_LIMIT = 100
    const val SEED_SILO_LIMIT = 25
    const val DECOR_SHED_LIMIT = 25
    const val FEEDING_TROUGH_LIMIT = 9

    /** Max items the named storage can currently hold. */
    fun maxItems(storageId: String, hutchCapacityLevel: Int = 0): Int = when (storageId) {
        "SeedSilo" -> SEED_SILO_LIMIT
        "DecorShed" -> DECOR_SHED_LIMIT
        "FeedingTrough" -> FEEDING_TROUGH_LIMIT
        "PetHutch" -> PriceCalculator.calculateHutchCapacity(hutchCapacityLevel)
        else -> Int.MAX_VALUE
    }

    /** Next slot index for an append-style placement (= current item count). */
    fun nextIndex(currentItemCount: Int): Int = currentItemCount

    /**
     * Can a *non-stackable* item (pet, plant, produce) be added to a storage of
     * size `currentCount` against `max` capacity?
     */
    fun hasFreeSlot(currentCount: Int, max: Int): Boolean = currentCount < max

    /**
     * Can a *stackable* item (seed by species, decor by id) be added?
     * If a slot for that id already exists the item merges in — always fits.
     * Otherwise we need a free slot.
     */
    fun canAddStackable(
        currentCount: Int,
        max: Int,
        stackExists: Boolean,
    ): Boolean = stackExists || currentCount < max
}
