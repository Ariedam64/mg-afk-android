package com.mgafk.app.data.websocket.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Room metadata model.
 * Port of Websocket mg / state/models/room.js
 */
data class RoomModel(
    val roomId: String,
    val roomSessionId: String,
    val hostPlayerId: String,
    val selectedGame: String?,
    val dateRoomCreated: String?,
    val dateGameBegan: String?,
    val isGameStarting: Boolean,
    val timer: JsonObject? = null,
    val chat: JsonArray? = null,
    val gameVotes: JsonObject? = null,
) {
    companion object {
        fun fromState(data: JsonObject): RoomModel {
            return RoomModel(
                roomId = data.string("roomId"),
                roomSessionId = data.string("roomSessionId"),
                hostPlayerId = data.string("hostPlayerId"),
                selectedGame = data["selectedGame"]?.jsonPrimitive?.contentOrNull,
                dateRoomCreated = data["dateRoomCreated"]?.jsonPrimitive?.contentOrNull,
                dateGameBegan = data["dateGameBegan"]?.jsonPrimitive?.contentOrNull,
                isGameStarting = data["isGameStarting"]?.jsonPrimitive?.booleanOrNull ?: false,
                timer = data["timer"] as? JsonObject,
                chat = data["chat"] as? JsonArray,
                gameVotes = data["gameVotes"] as? JsonObject,
            )
        }
    }
}
