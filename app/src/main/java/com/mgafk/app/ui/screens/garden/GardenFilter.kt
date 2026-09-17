package com.mgafk.app.ui.screens.garden

/**
 * Narrows the garden down to what the filters ask for.
 *
 * Two of the filters are about a single crop and two are about the tile it grows on, which
 * matters for the patch plants: a Clover tile holds several crops at once and only some of them
 * carry the mutation being hunted. Such a tile is kept with only its matching crops, rather than
 * whole, so the player is not left picking through the slots that were filtered out.
 *
 * Rarity and the name search belong to the tile, so they keep or drop it entirely.
 */
internal object GardenFilter {

    fun apply(
        entries: List<GardenEntry>,
        rarity: String?,
        mutations: Set<String>,
        minSize: Double,
        query: String,
    ): List<GardenEntry> {
        val search = query.trim()
        return entries.mapNotNull { entry ->
            if (rarity != null && entry.rarity != rarity) return@mapNotNull null
            if (search.isNotBlank() && !entry.displayName.contains(search, ignoreCase = true)) {
                return@mapNotNull null
            }
            when (entry) {
                is GardenEntry.SingleCrop ->
                    entry.takeIf { matches(it.plant, mutations, minSize) }

                is GardenEntry.MultiSlotPlant -> {
                    val kept = entry.crops.filter { matches(it, mutations, minSize) }
                    when {
                        kept.isEmpty() -> null
                        // Untouched when everything matches, so nothing is copied for nothing.
                        kept.size == entry.crops.size -> entry
                        else -> entry.copy(crops = kept)
                    }
                }
            }
        }
    }

    /** The per-crop half of the filter: what this one crop carries and how big it grew. */
    private fun matches(crop: ResolvedPlant, mutations: Set<String>, minSize: Double): Boolean {
        // Mutations combine with AND: a crop has to carry all of them, not any.
        if (!mutations.all { it in crop.snapshot.mutations }) return false
        return minSize <= 0.0 || crop.snapshot.size.toDouble() >= minSize
    }
}
