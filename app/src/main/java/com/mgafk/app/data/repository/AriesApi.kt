package com.mgafk.app.data.repository

import com.mgafk.app.data.AppLog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Client for the Aries Mod API — public rooms listing.
 */
object AriesApi {
    private const val TAG = "AriesApi"
    private const val BASE_URL = "https://ariesmod-api.ariedam.fr"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class PublicRoom(
        val id: String,
        val playersCount: Int,
        val players: List<RoomPlayer>,
    )

    data class RoomPlayer(
        val name: String,
        val avatarUrl: String?,
    )

    /**
     * Fetch all available public rooms.
     */
    fun fetchRooms(limit: Int = 500): List<PublicRoom> {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/rooms?limit=$limit")
                .header("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                AppLog.w(TAG, "HTTP ${response.code} fetching rooms")
                return emptyList()
            }
            val body = response.body?.string() ?: return emptyList()
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val array = json.parseToJsonElement(body) as? JsonArray ?: return emptyList()

            array.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val isPrivate = obj["is_private"]?.jsonPrimitive?.contentOrNull == "true"
                if (isPrivate) return@mapNotNull null

                val playersCount = obj["players_count"]?.jsonPrimitive?.intOrNull ?: 0
                val userSlots = (obj["user_slots"] as? JsonArray)?.mapNotNull { slotEl ->
                    val slot = slotEl as? JsonObject ?: return@mapNotNull null
                    val name = slot["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val avatarUrl = slot["avatar_url"]?.jsonPrimitive?.contentOrNull
                    RoomPlayer(name = name, avatarUrl = avatarUrl)
                } ?: emptyList()

                PublicRoom(id = id, playersCount = playersCount, players = userSlots)
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to fetch rooms: ${e.message}")
            emptyList()
        }
    }
}
