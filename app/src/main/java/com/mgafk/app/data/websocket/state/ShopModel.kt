package com.mgafk.app.data.websocket.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Shop model with inventory and restock timer.
 * Port of Websocket mg / state/models/shop.js
 */
data class ShopModel(
    val type: String,
    val inventory: JsonArray = JsonArray(emptyList()),
    /** Id of the restock the inventory belongs to. Null before the shop's first restock. */
    val restockId: String? = null,
    /**
     * When the stock on display runs out. Bundle 1422 sends this instead of a
     * `secondsUntilRestock` countdown: the game counts down to it itself, and so do we
     * (see [com.mgafk.app.data.model.ShopSnapshot.secondsUntilRestock]).
     */
    val deadlineMs: Long = 0L,
) {
    /** Items with initialStock > 0 */
    fun getAvailable(): List<JsonObject> =
        inventory.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val stock = obj["initialStock"]?.jsonPrimitive?.intOrNull ?: 0
            if (stock > 0) obj else null
        }

    /** Items with initialStock == 0 */
    fun getOutOfStock(): List<JsonObject> =
        inventory.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val stock = obj["initialStock"]?.jsonPrimitive?.intOrNull ?: 0
            if (stock == 0) obj else null
        }

    /**
     * Item name list. Reads each entry's `itemType` to pick the right id field
     * - necessary since the `tool` shop now mixes Tool and Decor entries
     * (FeedingTrough, SeedSilo, etc. are itemType=Decor but live under tool shop).
     */
    fun getItemNames(): List<String> =
        getAvailable().mapNotNull { obj ->
            val itemType = obj["itemType"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val key = keyForItemType(itemType) ?: return@mapNotNull null
            obj[key]?.jsonPrimitive?.contentOrNull
        }

    /** Get item name → initialStock mapping */
    fun getItemStocks(): Map<String, Int> =
        getAvailable().mapNotNull { obj ->
            val itemType = obj["itemType"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val key = keyForItemType(itemType) ?: return@mapNotNull null
            val name = obj[key]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val stock = obj["initialStock"]?.jsonPrimitive?.intOrNull ?: 0
            name to stock
        }.toMap()

    /**
     * What the player bought in THIS restock, from their `shopPurchases[type]` entry.
     *
     * Since game version 1284 an entry is `{ restockId, startedAtMs, purchases }` and is no
     * longer cleared when the shop restocks: last cycle's counts stay there until the next
     * purchase. The game only applies them while the entry's restockId matches the shop's
     * (bootScreen, v1292); anything else counts as nothing bought. Applying them anyway is what
     * made restocked eggs and Crop Cleansers show up, then drop to zero on the next update.
     */
    fun purchasesThisRestock(entry: JsonObject?): JsonObject? {
        if (entry == null) return null
        val entryRestock = entry["restockId"]?.jsonPrimitive?.contentOrNull ?: return null
        if (restockId == null || entryRestock != restockId) return null
        return entry["purchases"] as? JsonObject
    }

    private fun keyForItemType(itemType: String): String? = when (itemType) {
        "Seed" -> "species"
        "Tool" -> "toolId"
        "Egg" -> "eggId"
        "Decor" -> "decorId"
        else -> null
    }

    companion object {
        fun fromState(type: String, data: JsonObject): ShopModel {
            return ShopModel(
                type = type,
                inventory = data["inventory"] as? JsonArray ?: JsonArray(emptyList()),
                restockId = (data["restockId"] as? JsonPrimitive)?.contentOrNull,
                deadlineMs = data["deadlineMs"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }
    }
}
