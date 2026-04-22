package com.mgafk.app.data.websocket

import com.mgafk.app.data.AppLog
import com.mgafk.app.data.model.AbilityLog
import com.mgafk.app.data.model.ChatMessage
import com.mgafk.app.data.model.PlayerSnapshot
import com.mgafk.app.data.model.ReconnectConfig
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.data.websocket.state.GameState
import com.mgafk.app.data.websocket.state.GardenTile
import com.mgafk.app.data.websocket.state.PetInfo
import com.mgafk.app.data.websocket.state.ShopModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.mgafk.app.data.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

sealed class ClientEvent {
    data class StatusChanged(
        val status: SessionStatus,
        val message: String = "",
        val code: Int? = null,
        val room: String = "",
        val playerId: String = "",
        val retry: Int = 0,
        val maxRetries: Int = 0,
        val retryInMs: Long = 0,
    ) : ClientEvent()

    data class PlayersChanged(val count: Int) : ClientEvent()
    data class UptimeChanged(val text: String) : ClientEvent()
    data class AbilityLogged(val log: AbilityLog) : ClientEvent()
    data class LiveStatusChanged(
        val playerId: String,
        val playerName: String,
        val roomId: String,
        val weather: String,
        val pets: List<PetInfo>,
    ) : ClientEvent()

    data class ShopsChanged(val shops: List<ShopModel>, val shopPurchases: JsonObject? = null) : ClientEvent()
    data class GardenChanged(val plants: List<GardenTile>) : ClientEvent()
    data class EggsChanged(val eggs: List<GardenTile>) : ClientEvent()
    data class InventoryChanged(val items: JsonArray, val storages: JsonArray, val favoritedItemIds: List<String> = emptyList(), val magicDust: Double = 0.0) : ClientEvent()
    data class ChatChanged(val messages: List<ChatMessage>) : ClientEvent()
    data class PlayersListChanged(val players: List<PlayerSnapshot>) : ClientEvent()
    data class DebugLog(val level: String, val message: String, val detail: String = "") : ClientEvent()
}

class RoomClient {
    companion object {
        private const val TAG = "RoomClient"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = AppJson.default
    private val httpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var socketToken = 0
    private var state = "idle"

    // Connection state
    private var host = Constants.DEFAULT_HOST
    private var version = Constants.DEFAULT_VERSION
    var room = ""
        private set
    var playerId = ""
        private set
    private var cookie = ""
    private var userAgent = Constants.DEFAULT_UA

    // Game state — managed by GameState
    val gameState = GameState()

    // Actions — available after connection
    val actions = GameActions { text -> send(text) }

    // Tracking for dedup
    private var welcomed = false
    private var connectedAt = 0L
    private var manualClose = false
    private var playerCount = 0
    private var lastLivePayload: ClientEvent.LiveStatusChanged? = null
    private var lastShopsPayload: ClientEvent.ShopsChanged? = null
    private var lastGardenPayload: ClientEvent.GardenChanged? = null
    private var lastEggsPayload: ClientEvent.EggsChanged? = null
    private var lastInventoryPayload: ClientEvent.InventoryChanged? = null
    private var lastAbilityTimestamp = 0L
    private var lastChatSize = -1
    private var lastPlayersPayload: ClientEvent.PlayersListChanged? = null

    // Retry state
    private var retryCount = 0
    private var retryCode: Int? = null
    private var retryJob: Job? = null
    private var hasEverWelcomed = false
    private var initialConnectFastRetry = false
    var reconnectConfig = ReconnectConfig()

    // Last connect options for retry
    private var lastConnectOpts: ConnectOptions? = null

    private val _events = MutableSharedFlow<ClientEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ClientEvent> = _events.asSharedFlow()

    data class ConnectOptions(
        val version: String,
        val room: String,
        val cookie: String,
        val host: String,
        val userAgent: String,
    )

    fun connect(
        version: String,
        cookie: String,
        room: String = "",
        host: String = Constants.DEFAULT_HOST,
        userAgent: String = Constants.DEFAULT_UA,
        reconnect: ReconnectConfig? = null,
        isRetry: Boolean = false,
    ): String {
        val nextCookie = IdGenerator.normalizeCookie(cookie)
        val nextRoom = room.trim().ifEmpty { IdGenerator.generateRoomId() }

        if (nextCookie.isEmpty() || version.isBlank()) {
            throw IllegalArgumentException("Missing version or cookie")
        }

        webSocket?.let { disconnect() }

        if (!isRetry) {
            retryCount = 0
            retryCode = null
            initialConnectFastRetry = !hasEverWelcomed
        }
        cancelRetryJob()

        if (reconnect != null) this.reconnectConfig = reconnect

        this.host = host
        this.version = version.trim()
        this.room = nextRoom
        this.cookie = nextCookie
        this.userAgent = userAgent
        this.playerId = IdGenerator.generatePlayerId()
        this.playerCount = 0
        this.connectedAt = 0
        this.welcomed = false
        this.manualClose = false
        this.lastLivePayload = null
        this.lastShopsPayload = null
        this.lastGardenPayload = null
        this.lastEggsPayload = null
        this.lastInventoryPayload = null
        gameState.reset()

        lastConnectOpts = ConnectOptions(
            version = this.version,
            room = this.room,
            cookie = this.cookie,
            host = this.host,
            userAgent = this.userAgent,
        )

        val url = UrlBuilder.buildUrl(this.host, this.version, this.room, this.playerId)
        AppLog.d(TAG, "connect() url=$url isRetry=$isRetry retryCount=$retryCount")

        state = "connecting"
        emitStatus(SessionStatus.CONNECTING)

        val token = ++socketToken
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", this.userAgent)
            .header("Cookie", this.cookie)
            .header("Origin", "https://${this.host}")
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (token != socketToken) return
                handleOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (token != socketToken) return
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (token != socketToken) return
                handleClose(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (token != socketToken) return
                handleError(t)
            }
        })

        return url
    }

    fun disconnect() {
        state = "disconnected"
        connectedAt = 0
        welcomed = false
        playerCount = 0
        manualClose = true
        cancelRetryJob()
        retryCount = 0
        retryCode = null
        initialConnectFastRetry = false
        gameState.reset()
        emitStatus(SessionStatus.IDLE, code = 1000)

        val ws = webSocket
        webSocket = null
        socketToken++
        ws?.close(1000, "client disconnect")
    }

    fun dispose() {
        disconnect()
        scope.cancel()
    }

    // ---- Internal handlers ----

    private fun handleOpen() {
        AppLog.d(TAG, "onOpen — sending handshake")
        actions.voteForGame()
        actions.setSelectedGame()
    }

    private fun handleMessage(raw: String) {
        if (raw == "ping" || raw == "\"ping\"") {
            send("pong")
            return
        }

        val msg: JsonObject = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: Exception) {
            return
        }

        val type = msg["type"]?.jsonPrimitive?.contentOrNull
        AppLog.d(TAG, "onMessage type=$type")

        try {
            when (type) {
                "Welcome" -> handleWelcome(msg)
                "PartialState" -> handlePartialState(msg)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error processing $type: ${e.message}", e)
        }
    }

    private fun handleWelcome(msg: JsonObject) {
        AppLog.d(TAG, "handleWelcome — playerId=$playerId")
        // Check auth before accepting state
        val fullState = msg["fullState"]?.jsonObject ?: run {
            AppLog.w(TAG, "Welcome missing fullState!")
            return
        }
        val roomData = fullState["data"] as? JsonObject
        val players = roomData?.get("players") as? JsonArray
        val me = players?.firstOrNull { el ->
            (el as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull == playerId
        } as? JsonObject
        if (me != null && me["databaseUserId"]?.jsonPrimitive?.contentOrNull == null) {
            AppLog.e(TAG, "Auth failed — player found but no databaseUserId")
            failAuth("Invalid mc_jwt cookie.")
            return
        }
        AppLog.d(TAG, "Auth OK — me=${me != null} players=${players?.size ?: 0}")

        // Delegate full state handling to GameState
        gameState.handleMessage(msg)

        // Set lastAbilityTimestamp to the latest existing log so we don't re-emit old ones
        val myPlayer = gameState.getPlayer(playerId)
        val latestLog = (myPlayer?.activityLogs?.lastOrNull() as? JsonObject)
            ?.get("timestamp")?.jsonPrimitive?.longOrNull
        if (latestLog != null) lastAbilityTimestamp = latestLog

        emitPlayerCount()
        emitLiveStatus()
        emitShops()
        emitGarden()
        emitEggs()
        emitInventory()
        emitChat()
        emitPlayersList()

        if (!welcomed) {
            welcomed = true
            connectedAt = System.currentTimeMillis()
            state = "connected"
            val wasRetry = retryCount > 0
            retryCount = 0
            retryCode = null
            hasEverWelcomed = true
            initialConnectFastRetry = false
            cancelRetryJob()
            emit(ClientEvent.DebugLog(
                "info",
                if (wasRetry) "reconnected" else "connected",
                "room=$room player=$playerId",
            ))
            emitStatus(SessionStatus.CONNECTED, room = room, playerId = playerId)
        }
    }

    private fun handlePartialState(msg: JsonObject) {
        val patches = msg["patches"] as? JsonArray

        // Check if any patch touches our player's activityLogs
        val userSlotIndex = gameState.findUserSlotIndex(playerId)
        val touchesLogs = userSlotIndex != null && patches?.any { el ->
            val path = (el as? JsonObject)?.get("path")?.jsonPrimitive?.contentOrNull
            path != null && path.startsWith("/child/data/userSlots/$userSlotIndex/data/activityLogs")
        } == true

        // Delegate patch application to GameState
        gameState.handleMessage(msg)

        // If activityLogs were touched, check for new abilities
        if (touchesLogs) emitNewAbilityLogs()

        emitPlayerCount()
        emitLiveStatus()
        emitShops()
        emitGarden()
        emitEggs()
        emitInventory()
        emitChat()
        emitPlayersList()
    }

    private fun emitNewAbilityLogs() {
        val me = gameState.getPlayer(playerId) ?: return
        val logs = me.activityLogs

        // Collect new entries (oldest first so prepend in ViewModel keeps correct order)
        val newEntries = mutableListOf<AbilityLog>()

        for (i in 0 until logs.size) {
            val entry = logs[i] as? JsonObject ?: continue
            val timestamp = entry["timestamp"]?.jsonPrimitive?.longOrNull ?: continue
            if (timestamp <= lastAbilityTimestamp) continue

            val action = entry["action"]?.jsonPrimitive?.contentOrNull
            if (!Constants.isAbilityName(action)) continue

            val parameters = entry["parameters"]?.let { it as? JsonObject }
            val pet = parameters?.get("pet")?.let { it as? JsonObject }

            // Extract flat params map for AbilityFormatter descriptions
            val params = buildMap<String, String> {
                if (parameters != null) {
                    for ((key, value) in parameters) {
                        if (key == "pet") {
                            // Flatten pet id for self-check
                            pet?.get("id")?.jsonPrimitive?.contentOrNull?.let { put("petId", it) }
                            continue
                        }
                        when {
                            key == "targetPet" -> {
                                val target = value as? JsonObject
                                target?.get("name")?.jsonPrimitive?.contentOrNull?.let { put("targetPetName", it) }
                                target?.get("petSpecies")?.jsonPrimitive?.contentOrNull?.let { put("targetPetSpecies", it) }
                                target?.get("id")?.jsonPrimitive?.contentOrNull?.let { put("targetPetId", it) }
                            }
                            key == "harvestedCrop" -> {
                                val crop = value as? JsonObject
                                crop?.get("species")?.jsonPrimitive?.contentOrNull?.let { put("harvestedCropSpecies", it) }
                            }
                            key == "extraPet" -> {
                                val extra = value as? JsonObject
                                extra?.get("petSpecies")?.jsonPrimitive?.contentOrNull?.let { put("extraPetSpecies", it) }
                            }
                            key == "growSlot" -> {
                                val slot = value as? JsonObject
                                slot?.get("species")?.jsonPrimitive?.contentOrNull?.let { put("growSlotSpecies", it) }
                            }
                            key == "cropsRefunded" -> {
                                val arr = value as? JsonArray
                                put("cropsRefundedCount", (arr?.size ?: 0).toString())
                            }
                            key == "petsAffected" -> {
                                val arr = value as? JsonArray
                                put("petsAffectedCount", (arr?.size ?: 0).toString())
                            }
                            key == "eggsAffected" -> {
                                val arr = value as? JsonArray
                                put("eggsAffectedCount", (arr?.size ?: 0).toString())
                            }
                            key == "growSlotsAffected" -> {
                                val arr = value as? JsonArray
                                put("growSlotsAffectedCount", (arr?.size ?: 0).toString())
                            }
                            else -> {
                                // Store primitive values as strings
                                try {
                                    val content = value.jsonPrimitive.contentOrNull
                                    if (content != null) put(key, content)
                                } catch (_: IllegalArgumentException) {
                                    // Skip non-primitive values (arrays, objects)
                                }
                            }
                        }
                    }
                }
            }

            newEntries.add(
                AbilityLog(
                    timestamp = timestamp,
                    action = action.orEmpty(),
                    petName = pet?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    petSpecies = pet?.get("petSpecies")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    slotIndex = me.slotIndex ?: 0,
                    params = params,
                )
            )
        }

        // Emit oldest first → ViewModel prepends each → most recent ends up on top
        for (log in newEntries) {
            _events.tryEmit(ClientEvent.AbilityLogged(log))
        }

        // Update last seen timestamp
        val latestTimestamp = (logs.lastOrNull() as? JsonObject)
            ?.get("timestamp")?.jsonPrimitive?.longOrNull
        if (latestTimestamp != null && latestTimestamp > lastAbilityTimestamp) {
            lastAbilityTimestamp = latestTimestamp
        }
    }

    private fun handleClose(code: Int, reason: String) {
        AppLog.w(TAG, "onClose code=$code reason=$reason manualClose=$manualClose")
        state = "disconnected"
        connectedAt = 0
        welcomed = false
        webSocket = null

        emit(ClientEvent.DebugLog("info", "ws closed", "code=$code reason=$reason"))

        if (!manualClose) {
            if (shouldReconnect(code)) {
                if (scheduleReconnect(code, reason)) return
            }
            emitStatus(SessionStatus.IDLE, code = code)
            return
        }
        emitStatus(SessionStatus.IDLE, code = code)
    }

    private fun handleError(throwable: Throwable) {
        val msg = throwable.message ?: throwable.toString()
        AppLog.e(TAG, "onError: $msg", throwable)
        emit(ClientEvent.DebugLog("error", "ws error", msg))
        handleClose(1006, msg)
    }

    private fun failAuth(message: String) {
        AppLog.e(TAG, "failAuth: $message")
        cancelRetryJob()
        state = "error"
        connectedAt = 0
        welcomed = false
        playerCount = 0
        retryCount = 0
        retryCode = null
        initialConnectFastRetry = false
        gameState.reset()
        emitStatus(SessionStatus.ERROR, message = message, code = 4800)
        val ws = webSocket
        webSocket = null
        socketToken++
        ws?.close(1000, "auth failed")
    }

    // ---- Reconnection ----

    private fun shouldReconnect(code: Int): Boolean {
        if (code !in Constants.KNOWN_CLOSE_CODES) return reconnectConfig.unknown
        return reconnectConfig.codes[code] ?: reconnectConfig.unknown
    }

    private fun getReconnectDelay(code: Int): Long {
        val configuredDelay = if (code in Constants.SUPERSEDED_CODES) {
            max(0, reconnectConfig.delays.supersededMs)
        } else {
            max(0, reconnectConfig.delays.otherMs)
        }
        val base = max(configuredDelay, Constants.RETRY_DELAY_MS)
        val maxDelay = max(reconnectConfig.delays.maxDelayMs, Constants.RETRY_DELAY_MS)
        val backoff = min(
            base * 2.0.pow(max(0, retryCount - 1).toDouble()).toLong(),
            maxDelay,
        )
        val jitter = (Math.random() * Constants.RETRY_JITTER_MS).toLong()
        return backoff + jitter
    }

    private fun getMaxRetries(code: Int): Int =
        if (code == 4800) Constants.AUTH_RETRY_MAX else Constants.RETRY_MAX

    private fun scheduleReconnect(code: Int, reason: String): Boolean {
        val isInitial = initialConnectFastRetry && !hasEverWelcomed
        val maxRetries = if (isInitial) 5 else getMaxRetries(code)
        if (!isInitial && !shouldReconnect(code)) return false
        if (lastConnectOpts == null) return false
        retryCode = code
        if (retryCount >= maxRetries) {
            emit(ClientEvent.DebugLog("warn", "retries exhausted", "max=${if (maxRetries == Int.MAX_VALUE) "∞" else "$maxRetries"} code=$code"))
            return false
        }
        retryCount++
        val attempt = retryCount
        val delayMs = if (isInitial) 0L else getReconnectDelay(code)

        state = "connecting"
        val displayMax = if (maxRetries == Int.MAX_VALUE) "\u221E" else "$maxRetries"
        emit(ClientEvent.StatusChanged(
            status = SessionStatus.CONNECTING,
            message = "Reconnecting ($attempt/$displayMax)...",
            code = code,
            retry = attempt,
            maxRetries = maxRetries,
            retryInMs = delayMs,
        ))

        cancelRetryJob()
        retryJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            emit(ClientEvent.DebugLog("info", "reconnect attempt", "attempt=$attempt/$displayMax code=$code"))
            val opts = lastConnectOpts ?: return@launch
            try {
                connect(
                    version = opts.version,
                    cookie = opts.cookie,
                    room = opts.room,
                    host = opts.host,
                    userAgent = opts.userAgent,
                    isRetry = true,
                )
            } catch (e: Exception) {
                emit(ClientEvent.DebugLog("error", "reconnect failed", e.message ?: e.toString()))
                emitStatus(SessionStatus.ERROR, message = e.message ?: e.toString())
            }
        }
        return true
    }

    /**
     * Called when network becomes available again — skip the current retry delay
     * and reconnect immediately if we're in a retry cycle.
     */
    fun retryNow() {
        if (state != "connecting" || lastConnectOpts == null) return
        emit(ClientEvent.DebugLog("info", "network back", "forcing immediate retry"))
        cancelRetryJob()
        retryJob = scope.launch {
            val opts = lastConnectOpts ?: return@launch
            try {
                connect(
                    version = opts.version,
                    cookie = opts.cookie,
                    room = opts.room,
                    host = opts.host,
                    userAgent = opts.userAgent,
                    isRetry = true,
                )
            } catch (e: Exception) {
                emit(ClientEvent.DebugLog("error", "reconnect failed", e.message ?: e.toString()))
                emitStatus(SessionStatus.ERROR, message = e.message ?: e.toString())
            }
        }
    }

    private fun cancelRetryJob() {
        retryJob?.cancel()
        retryJob = null
    }

    // ---- Emitters (convert GameState models → ClientEvents) ----

    private fun emit(event: ClientEvent) {
        _events.tryEmit(event)
    }

    private fun emitStatus(
        status: SessionStatus,
        message: String = "",
        code: Int? = null,
        room: String = "",
        playerId: String = "",
    ) {
        emit(ClientEvent.StatusChanged(status = status, message = message, code = code, room = room, playerId = playerId))
    }

    private fun emitPlayerCount() {
        val count = gameState.getConnectedPlayers().size
        if (count != playerCount) {
            playerCount = count
            emit(ClientEvent.PlayersChanged(count))
        }
    }

    private fun emitLiveStatus() {
        val me = gameState.getPlayer(playerId)
        val payload = ClientEvent.LiveStatusChanged(
            playerId = playerId,
            playerName = me?.name.orEmpty(),
            roomId = gameState.getRoom()?.roomId.orEmpty(),
            weather = Constants.formatWeather(gameState.getWeather()),
            pets = me?.getActivePetInfos() ?: emptyList(),
        )
        if (payload == lastLivePayload) return
        lastLivePayload = payload
        emit(payload)
    }

    private fun emitShops() {
        val shops = gameState.getAllShops()
        if (shops.isEmpty()) return
        val me = gameState.getPlayer(playerId)
        val payload = ClientEvent.ShopsChanged(shops, me?.shopPurchases)
        if (payload == lastShopsPayload) return
        lastShopsPayload = payload
        emit(payload)
    }

    private fun emitGarden() {
        val me = gameState.getPlayer(playerId) ?: return
        val plants = me.getGardenPlants()
        val payload = ClientEvent.GardenChanged(plants)
        if (payload == lastGardenPayload) return
        lastGardenPayload = payload
        emit(payload)
    }

    private fun emitInventory() {
        val me = gameState.getPlayer(playerId) ?: return
        val payload = ClientEvent.InventoryChanged(me.inventory, me.storages, me.favoritedItemIds, me.magicDust)
        if (payload == lastInventoryPayload) return
        lastInventoryPayload = payload
        emit(payload)
    }

    private fun emitEggs() {
        val me = gameState.getPlayer(playerId) ?: return
        val eggs = me.getGardenEggs()
        val payload = ClientEvent.EggsChanged(eggs)
        if (payload == lastEggsPayload) return
        lastEggsPayload = payload
        emit(payload)
    }

    private fun emitChat() {
        val room = gameState.getRoom() ?: return
        val chatObj = room.chat ?: return
        val messagesArr = chatObj["messages"] as? JsonArray ?: return
        if (messagesArr.size == lastChatSize) return
        lastChatSize = messagesArr.size

        // Build player name lookup from room players
        val playerNames = mutableMapOf<String, String>()
        val roomData = gameState.roomState as? JsonObject
        val players = roomData?.get("players") as? JsonArray
        players?.forEach { el ->
            val obj = el as? JsonObject ?: return@forEach
            val pid = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            playerNames[pid] = name
        }

        val messages = messagesArr.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val pid = obj["playerId"]?.jsonPrimitive?.contentOrNull.orEmpty()
            ChatMessage(
                timestamp = obj["timestamp"]?.jsonPrimitive?.longOrNull ?: 0L,
                playerId = pid,
                playerName = playerNames[pid].orEmpty(),
                message = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
        emit(ClientEvent.ChatChanged(messages))
    }

    private fun emitPlayersList() {
        val allPlayers = gameState.getAllPlayers()
        if (allPlayers.isEmpty()) return

        val snapshots = allPlayers.map { player ->
            val cosmetic = player.cosmetic
            val avatar = cosmetic?.get("avatar") as? JsonArray
            val color = cosmetic?.get("color")?.jsonPrimitive?.contentOrNull.orEmpty()
            PlayerSnapshot(
                id = player.id,
                name = player.name,
                isConnected = player.isConnected,
                coins = player.coins,
                color = color,
                avatarBottom = avatar?.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty(),
                avatarMid = avatar?.getOrNull(1)?.jsonPrimitive?.contentOrNull.orEmpty(),
                avatarTop = avatar?.getOrNull(2)?.jsonPrimitive?.contentOrNull.orEmpty(),
                avatarExpression = avatar?.getOrNull(3)?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }
        val payload = ClientEvent.PlayersListChanged(snapshots)
        if (payload == lastPlayersPayload) return
        lastPlayersPayload = payload
        emit(payload)
    }

    private fun send(text: String) {
        webSocket?.send(text)
    }
}
