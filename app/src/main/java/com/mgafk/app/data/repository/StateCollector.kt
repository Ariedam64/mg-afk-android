package com.mgafk.app.data.repository

import com.mgafk.app.data.AppLog
import com.mgafk.app.data.websocket.RoomClient
import com.mgafk.app.data.websocket.state.PlayerModel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Reports each connected player's state to the backend (POST /collect-state)
 * so it can be persisted and used to drive the server-side "online" flag — a
 * player counts as online while their last collect-state is < 6 min old, so
 * this doubles as a keep-alive.
 *
 * Cadence is driven by [tick], which the caller invokes ~once a minute per
 * connected session:
 *  - send immediately when the player's reportable data changed since the
 *    last successful send;
 *  - otherwise send a keep-alive once [KEEPALIVE_MS] elapse with no change.
 *
 * The player is identified by their stable `databaseUserId` (Discord id): the
 * game's per-connection `p_…` id is rejected by the server.
 */
class StateCollector {
    companion object {
        private const val TAG = "StateCollector"
        private const val BASE_URL = "https://ariesmod-api.ariedam.fr"
        private const val KEEPALIVE_MS = 5 * 60 * 1000L
        private const val MOD_VERSION_PREFIX = "MG AFK App v"
        private const val MIN_PLAYER_ID_LENGTH = 3
        private const val AVATAR_SLOTS = 4
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Per-session dedup state: signature of the last payload + when it was sent. */
    private data class Tracking(val signature: String, val lastSentAt: Long)

    private val tracking = ConcurrentHashMap<String, Tracking>()

    /** Drop a session's dedup state (call on disconnect / removal). */
    fun reset(sessionId: String) {
        tracking.remove(sessionId)
    }

    /**
     * Evaluate one session and POST a collect-state when needed. Blocking;
     * call on an IO dispatcher. Safe to call before the session is fully
     * welcomed — it no-ops until the player (with a `databaseUserId`) resolves.
     *
     * @return true if a request was sent and accepted.
     */
    fun tick(sessionId: String, roomClient: RoomClient, appVersion: String, now: Long): Boolean {
        val playerId = roomClient.playerId
        if (playerId.isBlank()) return false
        val me = roomClient.gameState.getPlayer(playerId) ?: return false

        val userId = me.databaseUserId
        if (userId == null || userId.length < MIN_PLAYER_ID_LENGTH) return false

        val slotData = roomClient.gameState.getRawUserSlotData(playerId)
        val stateObj = buildStateObject(slotData)
        val signature = "${me.coins.toLong()}|$stateObj"

        val previous = tracking[sessionId]
        val changed = previous == null || previous.signature != signature
        val keepAliveDue = previous != null && (now - previous.lastSentAt) >= KEEPALIVE_MS
        if (!changed && !keepAliveDue) return false

        val body = buildBody(roomClient, me, userId, stateObj, appVersion)
        val sent = post(body)
        if (sent) {
            tracking[sessionId] = Tracking(signature, now)
        }
        return sent
    }

    // ---- Payload building ----

    /** Reportable state sub-trees, taken verbatim from the player's userSlot. */
    private fun buildStateObject(slotData: JsonObject?): JsonObject = buildJsonObject {
        if (slotData == null) return@buildJsonObject
        slotData["garden"]?.let { put("garden", it) }
        slotData["inventory"]?.let { put("inventory", it) }
        slotData["stats"]?.let { put("stats", it) }
        // Server field is "activityLog"; game stores it under "activityLogs".
        slotData["activityLogs"]?.let { put("activityLog", it) }
        slotData["journal"]?.let { put("journal", it) }
    }

    private fun buildBody(
        roomClient: RoomClient,
        me: PlayerModel,
        userId: String,
        stateObj: JsonObject,
        appVersion: String,
    ): JsonObject = buildJsonObject {
        put("playerId", userId)
        if (me.name.isNotBlank()) put("playerName", me.name)
        avatarArray(me)?.let { put("avatar", it) }
        put("coins", me.coins.toLong())
        put("room", buildRoom(roomClient))
        put("state", stateObj)
        put("modVersion", MOD_VERSION_PREFIX + appVersion)
    }

    /** The cosmetic avatar (exactly 4 non-blank layer filenames), or null if unusable. */
    private fun avatarArray(me: PlayerModel): JsonArray? {
        val avatar = me.cosmetic?.get("avatar") as? JsonArray ?: return null
        if (avatar.size != AVATAR_SLOTS) return null
        val layers = avatar.map { (it as? JsonPrimitive)?.contentOrNull }
        if (layers.any { it.isNullOrBlank() }) return null
        return buildJsonArray { layers.forEach { add(it!!) } }
    }

    private fun buildRoom(roomClient: RoomClient): JsonObject = buildJsonObject {
        val room = roomClient.gameState.getRoom()
        val players = roomClient.gameState.getAllPlayers()
        val roomId = room?.roomId
        put("id", if (roomId.isNullOrBlank()) JsonNull else JsonPrimitive(roomId))
        put("isPrivate", true)
        put("playersCount", players.size)
        put("userSlots", buildJsonArray {
            players.forEach { player ->
                val slotUserId = player.databaseUserId
                if (slotUserId.isNullOrBlank()) return@forEach
                add(buildJsonObject {
                    put("playerId", slotUserId)
                    put("name", player.name)
                    player.discordAvatarUrl?.let { put("avatarUrl", it) }
                    put("coins", player.coins.toLong())
                })
            }
        })
    }

    // ---- Transport ----

    private fun post(body: JsonObject): Boolean {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/collect-state")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.w(TAG, "HTTP ${response.code} on /collect-state")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "collect-state failed: ${e.message}")
            false
        }
    }
}
