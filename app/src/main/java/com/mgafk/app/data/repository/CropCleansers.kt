package com.mgafk.app.data.repository

import com.mgafk.app.data.model.InventoryToolItem

/**
 * Which crop cleansers the player holds, and which crops each one can cleanse.
 *
 * The game sends `CropCleanser {toolId, tileObjectIdx, growSlotIdx}` for every cleanser. A
 * cleanser removes the mutations of its `clearsMutationGroups`; MG API's catalogue is read first,
 * [KNOWN_GROUPS] and [MUTATION_GROUPS] are the fallback copied from the game bundle (version 2198).
 */
object CropCleansers {

    /** Cleanser tool id to the mutation groups it clears, in display order. */
    val KNOWN_GROUPS: Map<String, List<String>> = linkedMapOf(
        "CropCleanser" to listOf("Hydro", "Lunar"),
        "HydroCleanser" to listOf("Hydro"),
        "LunarCleanser" to listOf("Lunar"),
    )

    /**
     * Mutation to its group. Display names are listed with the ids (Amberlit, Dawnbound,
     * Amberbound) since the game and MG API use both. No cleanser clears Growth.
     */
    val MUTATION_GROUPS: Map<String, String> = mapOf(
        "Wet" to "Hydro",
        "Chilled" to "Hydro",
        "Frozen" to "Hydro",
        "Thunderstruck" to "Hydro",
        "Thundercharged" to "Hydro",
        "Dawnlit" to "Lunar",
        "Ambershine" to "Lunar",
        "Amberlit" to "Lunar",
        "Dawncharged" to "Lunar",
        "Dawnbound" to "Lunar",
        "Ambercharged" to "Lunar",
        "Amberbound" to "Lunar",
        "Gold" to "Growth",
        "Rainbow" to "Growth",
    )

    /** One cleanser the player holds: the tool, how many (stacks merged), the groups it clears. */
    data class Offer(val toolId: String, val quantity: Int, val groups: List<String>)

    /** The groups [toolId] clears: the catalogue first, then [KNOWN_GROUPS], else none. */
    fun groupsCleared(toolId: String, catalog: (String) -> MgApi.GameEntry?): List<String> =
        catalog(toolId)?.clearsMutationGroups?.takeIf { it.isNotEmpty() }
            ?: KNOWN_GROUPS[toolId]
            ?: emptyList()

    /** The group of [mutation]: MG API's `group` first, then [MUTATION_GROUPS], else null. */
    fun mutationGroup(mutation: String, mutations: (String) -> MgApi.MutationEntry?): String? =
        mutations(mutation)?.group?.takeIf { it.isNotBlank() } ?: MUTATION_GROUPS[mutation]

    /**
     * The held cleansers, stacks of the same tool merged, in [KNOWN_GROUPS] order then
     * alphabetical. Only tools that clear at least one group are listed.
     */
    fun offers(
        tools: List<InventoryToolItem>,
        catalog: (String) -> MgApi.GameEntry?,
    ): List<Offer> {
        val quantities = linkedMapOf<String, Int>()
        for (tool in tools) {
            if (tool.quantity <= 0 || tool.toolId.isBlank()) continue
            quantities[tool.toolId] = (quantities[tool.toolId] ?: 0) + tool.quantity
        }
        val knownOrder = KNOWN_GROUPS.keys.toList()
        return quantities.mapNotNull { (toolId, quantity) ->
            val groups = groupsCleared(toolId, catalog)
            if (groups.isEmpty()) return@mapNotNull null
            Offer(toolId = toolId, quantity = quantity, groups = groups)
        }.sortedWith(
            compareBy<Offer>(
                { knownOrder.indexOf(it.toolId).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                { it.toolId },
            )
        )
    }

    /** True when [offer] is held and the crop carries at least one mutation of its groups. */
    fun canCleanse(
        offer: Offer,
        cropMutations: List<String>,
        mutations: (String) -> MgApi.MutationEntry?,
    ): Boolean {
        if (offer.quantity <= 0) return false
        return cropMutations.any { mutation ->
            val group = mutationGroup(mutation, mutations)
            group != null && group in offer.groups
        }
    }

    /** One entry of the cleanser menu. */
    data class MenuItem(val offer: Offer, val label: String, val enabled: Boolean)

    /** What the cleanser button shows: its label, its menu, whether it can be pressed. */
    data class PickerState(val label: String, val items: List<MenuItem>, val enabled: Boolean)

    /**
     * The cleanser button for a crop carrying [cropMutations]. [displayName] turns a tool id into
     * its name (MgApi.itemDisplayName in the app).
     */
    fun pickerState(
        offers: List<Offer>,
        cropMutations: List<String>,
        mutations: (String) -> MgApi.MutationEntry?,
        displayName: (String) -> String,
    ): PickerState {
        val items = offers.map { offer ->
            MenuItem(
                offer = offer,
                label = "${displayName(offer.toolId)} x${offer.quantity}",
                enabled = canCleanse(offer, cropMutations, mutations),
            )
        }
        val label = when {
            cropMutations.isEmpty() -> "No mutations"
            offers.isEmpty() -> "No cleansers"
            else -> "Cleanse"
        }
        return PickerState(
            label = label,
            items = items,
            enabled = cropMutations.isNotEmpty() && items.any { it.enabled },
        )
    }
}
