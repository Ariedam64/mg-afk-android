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

    /** Get item name from the appropriate key for this shop type */
    fun getItemNames(): List<String> {
        val key = itemNameKey() ?: return emptyList()
        return getAvailable().mapNotNull { it[key]?.jsonPrimitive?.contentOrNull }
    }

    /** Get item name → initialStock mapping */
    fun getItemStocks(): Map<String, Int> {
        val key = itemNameKey() ?: return emptyMap()
        return getAvailable().mapNotNull { obj ->
            val name = obj[key]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val stock = obj["initialStock"]?.jsonPrimitive?.intOrNull ?: 0
            name to stock
        }.toMap()
    }

    private fun itemNameKey(): String? = when (type) {
        "seed" -> "species"
        "tool" -> "toolId"
        "egg" -> "eggId"
        "decor" -> "decorId"
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
