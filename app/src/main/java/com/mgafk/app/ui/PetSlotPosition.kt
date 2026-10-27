package com.mgafk.app.ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads a pet's tile position out of its entry in the player's petSlotInfos.
 * The current game (1753) describes it as `{ motion: { path: [{x,y}, ...], stepDurationMs,
 * startedAtMs } }`; a pet at rest, or one whose walk is over, stands on the last point of
 * `path`. Older shapes - `{ motion: { at: {x,y} } }` and a flatter `{ position: {x,y} }` -
 * are still checked as fallbacks.
 */
internal fun petTileFromSlotInfo(info: JsonObject): Pair<Double, Double>? {
    val motion = info["motion"] as? JsonObject
    val posObj = (motion?.get("path") as? JsonArray)?.lastOrNull() as? JsonObject
        ?: motion?.get("at") as? JsonObject
        ?: info["position"] as? JsonObject
        ?: return null
    val x = posObj["x"]?.jsonPrimitive?.doubleOrNull ?: return null
    val y = posObj["y"]?.jsonPrimitive?.doubleOrNull ?: return null
    return x to y
}
