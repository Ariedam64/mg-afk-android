package com.mgafk.app.ui.components

import com.mgafk.app.data.repository.MgApi

/** Canonical display order for mutations (lower index = shown first). */
private val MUTATION_ORDER = listOf(
    "Gold", "Rainbow", "Wet", "Chilled", "Frozen",
    "Thunderstruck", "Amberlit", "Amberbound", "Dawnlit", "Dawnbound",
)

/** Returns the sprite URL for a mutation name. */
fun mutationSpriteUrl(mutation: String): String = MgApi.mutationSpriteUrl(mutation)

/** Sorts a list of mutation names into the canonical display order. Unknown mutations go last, alphabetically. */
fun sortMutations(mutations: List<String>): List<String> {
    return mutations.sortedWith(compareBy<String> {
        val idx = MUTATION_ORDER.indexOf(it)
        if (idx >= 0) idx else MUTATION_ORDER.size
    }.thenBy { it })
}
