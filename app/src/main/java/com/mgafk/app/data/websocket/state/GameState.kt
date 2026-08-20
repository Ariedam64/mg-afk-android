package com.mgafk.app.data.websocket.state

import com.mgafk.app.data.websocket.JsonPatch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Central game state manager. Maintains raw state trees and builds typed models.
 * Port of Websocket mg / state/state.js
 *
 * Usage:
 *   val state = GameState()
 *   // On Welcome message:
 *   state.handleMessage(msg)
 *   // Access models:
 *   state.getPlayer(playerId)
 *   state.room
 *   state.getAllShops()
 */
class GameState {
    // Raw state (for patch application)
    var roomState: JsonElement? = null
        private set
    var gameState: JsonElement? = null
        private set
    var welcomed: Boolean = false
        private set

    // Built models
    private var _room: RoomModel? = null
    private val players = mutableMapOf<String, PlayerModel>()
    private val shops = mutableMapOf<String, ShopModel>()
    private var _weather: String? = null

    // --- Public API ---

    /**
     * Process any incoming WS message (Welcome or PartialState).
     * Returns true if state was updated.
     */
    fun handleMessage(msg: JsonObject): Boolean {
        val type = msg["type"]?.jsonPrimitive?.contentOrNull
        return when (type) {
            "Welcome" -> { handleWelcome(msg); true }
            "PartialState" -> handlePartialState(msg)
            else -> false
        }
    }

    fun getPlayer(playerId: String): PlayerModel? = players[playerId]
    fun getAllPlayers(): List<PlayerModel> = players.values.toList()
    fun getConnectedPlayers(): List<PlayerModel> = players.values.filter { it.isConnected }

    fun getShop(type: String): ShopModel? = shops[type]
    fun getAllShops(): List<ShopModel> = shops.values.toList()

    fun getRoom(): RoomModel? = _room
    fun getWeather(): String? = _weather

    /**
     * Find the user slot index for the given playerId.
     * Uses a 2-tier strategy: by owner id ([matchesPlayer]), then by the
     * player's database/Discord id ([matchesDb]).
     */
    fun findUserSlotIndex(playerId: String): Int? {
        val player = players[playerId] ?: return null
        // If already resolved via model building
        if (player.slotIndex != null) return player.slotIndex

        // Fallback: scan raw slots
        val slots = (gameState as? JsonObject)?.get("userSlots") as? JsonArray ?: return null
        val dbId = player.databaseUserId

        for (i in slots.indices) {
            val slot = slots[i] as? JsonObject ?: continue
            if (matchesPlayer(slot, playerId)) return i
        }

        if (dbId != null) {
            for (i in slots.indices) {
                val slot = slots[i] as? JsonObject ?: continue
                if (matchesDb(slot, dbId)) return i
            }
        }

        return null
    }

    /**
     * Raw `data` object of the given player's userSlot (garden / inventory /
     * stats / activityLogs / journal sub-trees), or null if the slot can't be
     * resolved. Used to report untyped state sub-trees verbatim without lossy
     * round-tripping through [PlayerModel].
     */
    fun getRawUserSlotData(playerId: String): JsonObject? {
        val slotIndex = findUserSlotIndex(playerId) ?: return null
        val slots = (gameState as? JsonObject)?.get("userSlots") as? JsonArray ?: return null
        val slot = slots.getOrNull(slotIndex) as? JsonObject ?: return null
        return slot["data"] as? JsonObject
    }

    fun reset() {
        roomState = null
        gameState = null
        welcomed = false
        _room = null
        players.clear()
        shops.clear()
        _weather = null
    }

    // --- Message handlers ---

    private fun handleWelcome(msg: JsonObject) {
        val fullState = msg["fullState"]?.jsonObject ?: return
        roomState = fullState["data"]
        gameState = fullState["child"]?.jsonObject?.get("data")
        welcomed = true
        buildModels()
    }

    /**
     * Apply patches from a PartialState message.
     * Returns true if any patches were applied.
     */
    private fun handlePartialState(msg: JsonObject): Boolean {
        val patches = msg["patches"] as? JsonArray ?: return false
        if (patches.isEmpty()) return false

        var changed = false

        for (patchEl in patches) {
            val patch = patchEl as? JsonObject ?: continue
            val path = patch["path"]?.jsonPrimitive?.contentOrNull ?: continue
            val value = patch["value"]
            val op = patch["op"]?.jsonPrimitive?.contentOrNull

            // Room state patches (players, room metadata)
            if (roomState != null && isRoomPatch(path)) {
                roomState = JsonPatch.applyPatch(roomState!!, path, value, op)
                changed = true
                continue
            }

            // Game state patches (everything under /child)
            if (gameState != null && path.startsWith("/child")) {
                val gamePath = path.removePrefix("/child")
                gameState = JsonPatch.applyPatch(gameState!!, gamePath, value, op)
                changed = true
                continue
            }
        }

        if (changed) {
            buildModels()
        }
        return changed
    }

    private fun isRoomPatch(path: String): Boolean {
        return path.matches(Regex("^/data/players/\\d+(/.*)?$")) ||
            path.matches(Regex("^/data/(roomId|roomSessionId|hostPlayerId|gameVotes|chat|selectedGame)(/.*)?$"))
    }

    // --- Model building ---

    private fun buildModels() {
        buildRoom()
        buildPlayers()
        buildShops()
        buildWeather()
    }

    private fun buildRoom() {
        val data = roomState as? JsonObject ?: return
        _room = RoomModel.fromState(data)
    }

    private fun buildPlayers() {
        val roomData = roomState as? JsonObject ?: return
        val roomPlayers = roomData["players"] as? JsonArray ?: return
        val userSlots = (gameState as? JsonObject)?.get("userSlots") as? JsonArray

        players.clear()

        for (roomPlayerEl in roomPlayers) {
            val roomPlayerObj = roomPlayerEl as? JsonObject ?: continue
            var player = PlayerModel.fromRoomPlayer(roomPlayerObj)

            // Find matching userSlot by playerId or databaseUserId
            if (userSlots != null) {
                for (i in userSlots.indices) {
                    val slot = userSlots[i] as? JsonObject ?: continue
                    if (matchesPlayer(slot, player.id) || matchesDbForPlayer(slot, player.databaseUserId)) {
                        player = PlayerModel.applySlot(player, slot, i)
                        break
                    }
                }
            }

            players[player.id] = player
        }
    }

    private fun buildShops() {
        val gameData = gameState as? JsonObject ?: return
        val shopsObj = gameData["shops"] as? JsonObject ?: return

        shops.clear()
        for ((type, data) in shopsObj) {
            val shopObj = data as? JsonObject ?: continue
            shops[type] = ShopModel.fromState(type, shopObj)
        }
    }

    private fun buildWeather() {
        _weather = (gameState as? JsonObject)?.get("weather")?.jsonPrimitive?.contentOrNull
    }

    // --- Slot matching helpers ---

    /**
     * The game renamed the slot's owner field from `playerId` to `userId`; the
     * value is unchanged - the id Welcome reports as `selfPlayerId`. Slots now
     * carry `playerId: null`, so matching on it alone silently matches nothing
     * and every slot-backed view (garden, inventory, storage, activity logs)
     * stays empty. Both names are accepted so an older deployment keeps working.
     */
    private fun matchesPlayer(slot: JsonObject, playerId: String): Boolean {
        if (playerId.isEmpty()) return false
        if (slot["userId"]?.jsonPrimitive?.contentOrNull == playerId) return true
        if (slot["playerId"]?.jsonPrimitive?.contentOrNull == playerId) return true
        val data = slot["data"] as? JsonObject
        return data?.get("playerId")?.jsonPrimitive?.contentOrNull == playerId
    }

    /**
     * Fallback identity match, used only when [matchesPlayer] found nothing.
     * The id fields sit at the slot's top level, but some payloads repeat them
     * inside `data`, so both levels are checked.
     */
    private fun matchesDb(slot: JsonObject, dbId: String): Boolean {
        if (dbId.isEmpty()) return false
        if (slot["discordUserId"]?.jsonPrimitive?.contentOrNull == dbId) return true
        if (slot["databaseUserId"]?.jsonPrimitive?.contentOrNull == dbId) return true
        val data = slot["data"] as? JsonObject ?: return false
        return data["discordUserId"]?.jsonPrimitive?.contentOrNull == dbId ||
            data["databaseUserId"]?.jsonPrimitive?.contentOrNull == dbId ||
            data["userId"]?.jsonPrimitive?.contentOrNull == dbId
    }

    private fun matchesDbForPlayer(slot: JsonObject, dbId: String?): Boolean {
        if (dbId == null) return false
        return matchesDb(slot, dbId)
    }
}
