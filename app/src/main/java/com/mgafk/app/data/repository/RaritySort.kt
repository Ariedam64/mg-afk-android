package com.mgafk.app.data.repository

/**
 * Sort key for listing items and pets rarest first, as the inventory, storage and team
 * screens do. Unknown or missing rarities go last.
 *
 * Note the order runs the other way from [MgApi.RARITY_ORDER] and also accepts "Mythical".
 */
object RaritySort {

    private val ORDER = listOf("Celestial", "Divine", "Mythic", "Mythical", "Legendary", "Rare", "Uncommon", "Common")

    /** Rarity index for sorting (lower = rarer = first), [ORDER]'s size when not ranked. */
    fun index(rarity: String?): Int {
        if (rarity == null) return ORDER.size
        return ORDER.indexOfFirst { it.equals(rarity, ignoreCase = true) }.let { if (it < 0) ORDER.size else it }
    }

    /** Sort key for an item id (seed, tool, egg, decor, crop). */
    fun item(itemId: String): Int = index(MgApi.findItem(itemId)?.rarity)

    /** Sort key for a pet species. */
    fun pet(species: String): Int = index(MgApi.findPet(species)?.rarity)
}
