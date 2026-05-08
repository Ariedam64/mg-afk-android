package com.mgafk.app.data.websocket.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Shop model with inventory and restock timer.
 * Port of Websocket mg / state/models/shop.js
 */
data class ShopModel(
    val type: String,
    val inventory: JsonArray = JsonArray(emptyList()),
    val secondsUntilRestock: Int = 0,
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
     * — necessary since the `tool` shop now mixes Tool and Decor entries
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
                secondsUntilRestock = data["secondsUntilRestock"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    }
}
