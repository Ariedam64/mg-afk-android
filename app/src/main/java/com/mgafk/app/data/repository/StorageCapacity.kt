package com.mgafk.app.data.repository

/**
 * Storage capacity rules ported from QuinoaView (function returning capacity per
 * storage id). Values are baked into the game and not exposed via /data, so we
 * mirror the same constants here.
 *
 *   SeedSilo     → 10 base, upgradable ("capacitySlots" on the storage)
 *   DecorShed    → 10 base, upgradable ("capacitySlots" on the storage)
 *   FeedingTrough→ 9
 *   PetHutch     → 10 base, upgradable ("capacitySlots" on the storage)
 *   ToolShack    → 10 base, upgradable ("capacitySlots" on the storage)
 *   Inventory    → 100 items total (stackable items merge with existing slots)
 */
object StorageCapacity {

    const val INVENTORY_LIMIT = 100
    const val FEEDING_TROUGH_LIMIT = 9

    /**
     * Max items the named storage can currently hold. For upgradable storages
     * (PetHutch, SeedSilo, DecorShed, ToolShack) pass the "capacitySlots" value read
     * from the game.
     */
    fun maxItems(
        storageId: String,
        hutchCapacitySlots: Int = PriceCalculator.HUTCH_BASE_CAPACITY,
        siloCapacitySlots: Int = PriceCalculator.SILO_BASE_CAPACITY,
        decorShedCapacitySlots: Int = PriceCalculator.DECOR_SHED_BASE_CAPACITY,
        toolShackCapacitySlots: Int = PriceCalculator.TOOL_SHACK_BASE_CAPACITY,
    ): Int = when (storageId) {
        "SeedSilo" -> siloCapacitySlots
        "DecorShed" -> decorShedCapacitySlots
        "FeedingTrough" -> FEEDING_TROUGH_LIMIT
        "PetHutch" -> hutchCapacitySlots
        "ToolShack" -> toolShackCapacitySlots
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
     * If a slot for that id already exists the item merges in - always fits.
     * Otherwise we need a free slot.
     */
    fun canAddStackable(
        currentCount: Int,
        max: Int,
        stackExists: Boolean,
    ): Boolean = stackExists || currentCount < max
}
