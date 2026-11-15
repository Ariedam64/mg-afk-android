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

    /** Where an [Offer] is spent from: the inventory first, else the Tool Shack. */
    enum class Source { INVENTORY, STORAGE }

    /**
     * One potion the plant detail can offer, held and stored stacks merged. [storageKey] is the
     * key of the first Tool Shack stack, null when none is stored. [canRetrieve] is false when a
     * stored potion has no room to come back to a full inventory.
     */
    data class Offer(
        val toolId: String,
        val mutation: String,
        val inventoryQuantity: Int,
        val storageQuantity: Int,
        val storageKey: String?,
        val canRetrieve: Boolean,
    ) {
        val source: Source get() = if (inventoryQuantity > 0) Source.INVENTORY else Source.STORAGE
    }

    /**
     * The mutation potions held in the inventory or kept in the Tool Shack, one offer per tool,
     * in the same order as [available]. [bagCount] is [StorageCapacity.bagCount] of the inventory.
     */
    fun offers(
        inventoryTools: List<InventoryToolItem>,
        toolShack: List<InventoryToolItem>,
        bagCount: Int,
        catalog: (String) -> MgApi.GameEntry?,
    ): List<Offer> {
        val held = linkedMapOf<String, Int>()
        for (tool in inventoryTools) {
            if (tool.quantity <= 0 || tool.toolId.isBlank()) continue
            held[tool.toolId] = (held[tool.toolId] ?: 0) + tool.quantity
        }
        val stored = linkedMapOf<String, Int>()
        val storageKeys = mutableMapOf<String, String>()
        for (tool in toolShack) {
            if (tool.quantity <= 0 || tool.toolId.isBlank()) continue
            stored[tool.toolId] = (stored[tool.toolId] ?: 0) + tool.quantity
            if (tool.toolId !in storageKeys) storageKeys[tool.toolId] = tool.storageKey
        }
        val knownOrder = KNOWN.keys.toList()
        return (held.keys + stored.keys).distinct().mapNotNull { toolId ->
            val mutation = grantedMutation(toolId, catalog) ?: return@mapNotNull null
            val inventoryQuantity = held[toolId] ?: 0
            Offer(
                toolId = toolId,
                mutation = mutation,
                inventoryQuantity = inventoryQuantity,
                storageQuantity = stored[toolId] ?: 0,
                storageKey = storageKeys[toolId],
                canRetrieve = StorageCapacity.canAddStackable(
                    currentCount = bagCount,
                    max = StorageCapacity.INVENTORY_LIMIT,
                    stackExists = inventoryQuantity > 0,
                ),
            )
        }.sortedWith(
            compareBy<Offer>(
                { knownOrder.indexOf(it.toolId).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                { it.toolId },
            )
        )
    }
}
