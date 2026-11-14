package com.mgafk.app.data.repository

import com.mgafk.app.data.model.InventoryToolItem

/**
 * Which mutation potions the player holds, and the mutation each one grants.
 *
 * The game sends `MutationPotion {mutation, tileObjectIdx, growSlotIdx}`, where `mutation` is the
 * potion's `grantedMutation`. MG API's catalogue is read first; [KNOWN] is the fallback copied
 * from the game bundle (version 2036) in case the API does not expose the field.
 */
object MutationPotions {

    /** Potion tool id to granted mutation, in display order. AmberlitPotion grants Ambershine. */
    val KNOWN: Map<String, String> = linkedMapOf(
        "WetPotion" to "Wet",
        "ChilledPotion" to "Chilled",
        "FrozenPotion" to "Frozen",
        "DawnlitPotion" to "Dawnlit",
        "AmberlitPotion" to "Ambershine",
        "GoldPotion" to "Gold",
        "RainbowPotion" to "Rainbow",
    )

    /** One potion the player can apply: the tool to spend, the mutation it grants, how many held. */
    data class Available(val toolId: String, val mutation: String, val quantity: Int)

    /** The mutation [toolId] grants: the catalogue first, then [KNOWN], else null. */
    fun grantedMutation(toolId: String, catalog: (String) -> MgApi.GameEntry?): String? =
        catalog(toolId)?.grantedMutation?.takeIf { it.isNotBlank() } ?: KNOWN[toolId]

    /**
     * The held potions that grant a mutation, stacks of the same tool merged, in [KNOWN] order then
     * alphabetical. Empty when the player holds none.
     */
    fun available(
        tools: List<InventoryToolItem>,
        catalog: (String) -> MgApi.GameEntry?,
    ): List<Available> {
        val quantities = linkedMapOf<String, Int>()
        for (tool in tools) {
            if (tool.quantity <= 0 || tool.toolId.isBlank()) continue
            quantities[tool.toolId] = (quantities[tool.toolId] ?: 0) + tool.quantity
        }
        val knownOrder = KNOWN.keys.toList()
        return quantities.mapNotNull { (toolId, quantity) ->
            val mutation = grantedMutation(toolId, catalog) ?: return@mapNotNull null
            Available(toolId = toolId, mutation = mutation, quantity = quantity)
        }.sortedWith(
            compareBy<Available>(
                { knownOrder.indexOf(it.toolId).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                { it.toolId },
            )
        )
    }
}
