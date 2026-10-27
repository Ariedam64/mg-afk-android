package com.mgafk.app.ui

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads a pet's tile position out of its entry in the player's petSlotInfos.
 * The game has used two shapes for this over time - `{ motion: { at: {x,y} } }`
 * (current) and a flatter `{ position: {x,y} }` (older) - so both are checked.
 */
internal fun petTileFromSlotInfo(info: JsonObject): Pair<Double, Double>? {
    val posObj = (info["motion"] as? JsonObject)?.get("at") as? JsonObject
        ?: info["position"] as? JsonObject
        ?: return null
    val x = posObj["x"]?.jsonPrimitive?.doubleOrNull ?: return null
    val y = posObj["y"]?.jsonPrimitive?.doubleOrNull ?: return null
    return x to y
}
