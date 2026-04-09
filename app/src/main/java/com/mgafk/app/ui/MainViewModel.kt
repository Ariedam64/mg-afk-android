package com.mgafk.app.ui

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import coil.imageLoader
import coil.request.ImageRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.AlertMode
import com.mgafk.app.data.model.AppSettings
import com.mgafk.app.data.model.WakeLockMode
import com.mgafk.app.data.model.ChatMessage
import com.mgafk.app.data.model.PlayerSnapshot
import com.mgafk.app.data.model.GardenEggSnapshot
import com.mgafk.app.data.model.GardenPlantSnapshot
import com.mgafk.app.data.model.InventoryEggItem
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventoryPlantItem
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.model.InventorySnapshot
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.model.InventoryCropsItem
import com.mgafk.app.data.model.InventoryDecorItem
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.model.ReconnectConfig
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.data.model.ShopSnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.SessionRepository
import com.mgafk.app.data.repository.AppRelease
import com.mgafk.app.data.repository.VersionFetcher
import com.mgafk.app.data.websocket.ClientEvent
import com.mgafk.app.data.websocket.RoomClient
import com.mgafk.app.service.AfkService
import com.mgafk.app.service.AlertNotifier
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun WakeLockMode.toServiceMode(): Int = when (this) {
    WakeLockMode.OFF -> AfkService.MODE_OFF
    WakeLockMode.SMART -> AfkService.MODE_SMART
    WakeLockMode.ALWAYS -> AfkService.MODE_ALWAYS
}

data class UiState(
    val sessions: List<Session> = listOf(Session()),
    val activeSessionId: String = "",
    val alerts: AlertConfig = AlertConfig(),
    val collapsedCards: Map<String, Boolean> = emptyMap(),
    val connecting: Boolean = false,
    val apiReady: Boolean = false,
    val loadingStep: String = "",
    val updateAvailable: AppRelease? = null,
    val purchaseError: String = "",
    val showShopTip: Boolean = false,
    val showTroughTip: Boolean = false,
    val showPetTip: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val serviceLogs: List<AfkService.ServiceLog> = emptyList(),
) {
    val activeSession: Session
        get() = sessions.find { it.id == activeSessionId } ?: sessions.first()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private companion object { const val TAG = "MainViewModel" }
    private val repo = SessionRepository(application)
    private val alertNotifier = AlertNotifier(application)
    private val clients = mutableMapOf<String, RoomClient>()
    private val collectorJobs = mutableMapOf<String, Job>()
    private var serviceRunning = false
    private val connectivityManager =
        application.getSystemService(ConnectivityManager::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Network just came back — force immediate retry on all reconnecting clients
            clients.forEach { (_, client) -> client.retryNow() }
        }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        viewModelScope.launch {
            val sessions = repo.loadSessions().ifEmpty { listOf(Session()) }
            val activeId = repo.loadActiveSessionId() ?: sessions.first().id
            val alerts = repo.loadAlerts()
            val collapsedCards = repo.loadCollapsedCards()
            val shopTipDismissed = repo.isShopTipDismissed()
            val troughTipDismissed = repo.isTroughTipDismissed()
            val petTipDismissed = repo.isPetTipDismissed()
            val settings = repo.loadSettings()
            _state.value = UiState(
                sessions = sessions,
                activeSessionId = activeId,
                alerts = alerts,
                collapsedCards = collapsedCards,
                showShopTip = !shopTipDismissed,
                showTroughTip = !troughTipDismissed,
                showPetTip = !petTipDismissed,
                settings = settings,
            )
            // Collect service logs (wake lock events etc.)
            launch {
                AfkService.logs.collect { log ->
                    _state.update { s ->
                        s.copy(serviceLogs = (listOf(log) + s.serviceLogs).take(100))
                    }
                }
            }
            // Preload ALL API data + sprites at startup
            launch {
                _state.update { it.copy(loadingStep = "Loading game data…") }
                MgApi.preloadAll()
                _state.update { it.copy(loadingStep = "Preloading sprites…") }
                preloadSprites()
                _state.update { it.copy(apiReady = true, loadingStep = "") }
            }
            // Check for app updates in background
            launch {
                val release = VersionFetcher.fetchLatestRelease() ?: return@launch
                val current = com.mgafk.app.BuildConfig.VERSION_NAME
                if (VersionFetcher.isNewer(current, release.tagName)) {
                    _state.update { it.copy(updateAvailable = release) }
                }
            }
        }
    }

    // ---- Session Management ----

    fun addSession() {
        _state.update { s ->
            val newSession = Session(name = "Session ${s.sessions.size + 1}")
            s.copy(sessions = s.sessions + newSession, activeSessionId = newSession.id)
        }
        persist()
    }

    fun removeSession(id: String) {
        collectorJobs.remove(id)?.cancel()
        val client = clients.remove(id)
        client?.dispose()
        _state.update { s ->
            val filtered = s.sessions.filter { it.id != id }
            val sessions = filtered.ifEmpty { listOf(Session()) }
            val activeId = if (s.activeSessionId == id) sessions.first().id else s.activeSessionId
            s.copy(sessions = sessions, activeSessionId = activeId)
        }
        persist()
    }

    fun selectSession(id: String) {
        _state.update { it.copy(activeSessionId = id) }
        viewModelScope.launch { repo.saveActiveSessionId(id) }
    }

    fun updateSession(id: String, transform: (Session) -> Session) {
        _state.update { s ->
            s.copy(sessions = s.sessions.map { if (it.id == id) transform(it) else it })
        }
        persist()
    }

    // ---- Connection ----

    private fun startAfkService() {
        if (serviceRunning) return
        val app = getApplication<Application>()
        val intent = Intent(app, AfkService::class.java)
            .putExtra(AfkService.EXTRA_WIFI_LOCK, _state.value.settings.wifiLockEnabled)
            .putExtra(AfkService.EXTRA_WAKE_LOCK_MODE, _state.value.settings.wakeLockMode.toServiceMode())
            .putExtra(AfkService.EXTRA_WAKE_LOCK_DELAY_MIN, _state.value.settings.wakeLockAutoDelayMin)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
        serviceRunning = true
    }

    private fun stopAfkServiceIfIdle() {
        val anyConnected = _state.value.sessions.any { it.connected }
        if (!anyConnected && serviceRunning) {
            val app = getApplication<Application>()
            app.stopService(Intent(app, AfkService::class.java))
            serviceRunning = false
        }
    }

    fun connect(sessionId: String) {
        val session = _state.value.sessions.find { it.id == sessionId } ?: return
        if (session.cookie.isBlank()) return

        startAfkService()
        updateSession(sessionId) { it.copy(busy = true, status = SessionStatus.CONNECTING) }

        viewModelScope.launch {
            try {
                val version = VersionFetcher.fetchGameVersion(
                    host = session.gameUrl.removePrefix("https://").removePrefix("http://").ifBlank { "magicgarden.gg" }
                )

                updateSession(sessionId) { it.copy(gameVersion = version) }
                val client = clients.getOrPut(sessionId) { RoomClient() }

                // Cancel previous collector before starting a new one
                collectorJobs[sessionId]?.cancel()
                collectorJobs[sessionId] = launch {
                    client.events.collect { event ->
                        handleClientEvent(sessionId, event)
                    }
                }

                val host = session.gameUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .ifBlank { "magicgarden.gg" }

                val s = _state.value.settings
                val reconnectWithSettings = session.reconnect.copy(
                    delays = session.reconnect.delays.copy(
                        otherMs = s.retryDelayMs,
                        supersededMs = s.retrySupersededDelayMs,
                        maxDelayMs = s.retryMaxDelayMs,
                    ),
                )
                client.connect(
                    version = version,
                    cookie = session.cookie,
                    room = session.room,
                    host = host,
                    reconnect = reconnectWithSettings,
                )
            } catch (e: Exception) {
                updateSession(sessionId) {
                    it.copy(
                        busy = false,
                        status = SessionStatus.ERROR,
                        error = e.message ?: "Connection failed",
                    )
                }
            }
        }
    }

    fun disconnect(sessionId: String) {
        collectorJobs.remove(sessionId)?.cancel()
        clients[sessionId]?.disconnect()
        updateSession(sessionId) {
            it.copy(
                connected = false,
                busy = false,
                status = SessionStatus.IDLE,
                players = 0,
                connectedAt = 0,
            )
        }
        stopAfkServiceIfIdle()
    }

    fun setToken(sessionId: String, token: String) {
        updateSession(sessionId) { it.copy(cookie = token) }
    }

    fun clearToken(sessionId: String) {
        updateSession(sessionId) { it.copy(cookie = "") }
    }

    fun clearLogs(sessionId: String) {
        updateSession(sessionId) { it.copy(logs = emptyList()) }
    }

    fun clearWsLogs(sessionId: String) {
        updateSession(sessionId) { it.copy(wsLogs = emptyList()) }
    }

    fun clearServiceLogs() {
        _state.update { it.copy(serviceLogs = emptyList()) }
    }

    // Optimistic purchase: decrement stock locally, send to server, rollback if no confirmation
    private val pendingPurchaseJobs = mutableMapOf<String, Job>()

    fun dismissShopTip() {
        _state.update { it.copy(showShopTip = false) }
        viewModelScope.launch { repo.dismissShopTip() }
    }

    fun dismissTroughTip() {
        _state.update { it.copy(showTroughTip = false) }
        viewModelScope.launch { repo.dismissTroughTip() }
    }

    fun dismissPetTip() {
        _state.update { it.copy(showPetTip = false) }
        viewModelScope.launch { repo.dismissPetTip() }
    }

    fun purchaseShopItem(sessionId: String, shopType: String, itemName: String) {
        val actions = clients[sessionId]?.actions ?: return

        // Check current stock before optimistic update
        val session = _state.value.sessions.find { it.id == sessionId } ?: return
        val shop = session.shops.find { it.type == shopType } ?: return
        val currentStock = shop.itemStocks[itemName] ?: 0
        if (currentStock <= 0) return

        // Optimistic: decrement stock locally
        val previousShops = session.shops
        updateSession(sessionId) { s ->
            s.copy(shops = s.shops.map { snap ->
                if (snap.type == shopType) {
                    snap.copy(itemStocks = snap.itemStocks.mapValues { (name, stock) ->
                        if (name == itemName) maxOf(0, stock - 1) else stock
                    })
                } else snap
            })
        }

        // Send to server
        when (shopType) {
            "seed" -> actions.purchaseSeed(itemName)
            "tool" -> actions.purchaseTool(itemName)
            "egg" -> actions.purchaseEgg(itemName)
            "decor" -> actions.purchaseDecor(itemName)
        }

        // Rollback after 5s if server hasn't confirmed (ShopsChanged would overwrite first)
        val key = "$sessionId:$shopType:$itemName"
        pendingPurchaseJobs[key]?.cancel()
        pendingPurchaseJobs[key] = viewModelScope.launch {
            delay(5000)
            // If we get here, server never confirmed — rollback
            updateSession(sessionId) { s ->
                val current = s.shops.find { it.type == shopType }
                if (current != null) s.copy(shops = previousShops) else s
            }
            pendingPurchaseJobs.remove(key)
            // Show error briefly
            _state.update { it.copy(purchaseError = "Purchase failed: $itemName") }
            delay(3000)
            _state.update { if (it.purchaseError == "Purchase failed: $itemName") it.copy(purchaseError = "") else it }
        }
    }

    fun purchaseAllShopItem(sessionId: String, shopType: String, itemName: String) {
        val actions = clients[sessionId]?.actions ?: return
        val session = _state.value.sessions.find { it.id == sessionId } ?: return
        val shop = session.shops.find { it.type == shopType } ?: return
        val currentStock = shop.itemStocks[itemName] ?: 0
        if (currentStock <= 0) return

        // Optimistic: set stock to 0
        val previousShops = session.shops
        updateSession(sessionId) { s ->
            s.copy(shops = s.shops.map { snap ->
                if (snap.type == shopType) {
                    snap.copy(itemStocks = snap.itemStocks.mapValues { (name, stock) ->
                        if (name == itemName) 0 else stock
                    })
                } else snap
            })
        }

        // Send N purchase commands to server
        val purchaseFn: (String) -> Unit = when (shopType) {
            "seed" -> actions::purchaseSeed
            "tool" -> actions::purchaseTool
            "egg" -> actions::purchaseEgg
            "decor" -> actions::purchaseDecor
            else -> return
        }
        repeat(currentStock) { purchaseFn(itemName) }

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:$shopType:$itemName"
        pendingPurchaseJobs[key]?.cancel()
        pendingPurchaseJobs[key] = viewModelScope.launch {
            delay(5000)
            updateSession(sessionId) { s ->
                val current = s.shops.find { it.type == shopType }
                if (current != null) s.copy(shops = previousShops) else s
            }
            pendingPurchaseJobs.remove(key)
            _state.update { it.copy(purchaseError = "Purchase failed: $itemName") }
            delay(3000)
            _state.update { if (it.purchaseError == "Purchase failed: $itemName") it.copy(purchaseError = "") else it }
        }
    }

    fun sendChat(sessionId: String, message: String) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return
        clients[sessionId]?.actions?.chat(trimmed)
    }

    private val pendingTroughJobs = mutableMapOf<String, Job>()

    fun putItemsInFeedingTrough(sessionId: String, produceItems: List<InventoryProduceItem>) {
        val actions = clients[sessionId]?.actions ?: return
        val session = _state.value.sessions.find { it.id == sessionId } ?: return
        val currentCount = session.feedingTrough.size
        val toAdd = produceItems.filterIndexed { i, _ -> currentCount + i < 9 }
        if (toAdd.isEmpty()) return

        // Optimistic: add crops to trough + remove from inventory
        val previousTrough = session.feedingTrough
        val previousInventory = session.inventory
        val addedIds = toAdd.map { it.id }.toSet()
        updateSession(sessionId) { s ->
            s.copy(
                feedingTrough = s.feedingTrough + toAdd.map { p ->
                    InventoryCropsItem(id = p.id, species = p.species, scale = p.scale, mutations = p.mutations)
                },
                inventory = s.inventory.copy(
                    produce = s.inventory.produce.filter { it.id !in addedIds }
                ),
            )
        }

        // Send to server
        toAdd.forEachIndexed { index, item ->
            actions.putItemInStorage(
                itemId = item.id,
                storageId = "FeedingTrough",
                toStorageIndex = currentCount + index,
            )
        }

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:trough:add:${toAdd.first().id}"
        pendingTroughJobs[key]?.cancel()
        pendingTroughJobs[key] = viewModelScope.launch {
            delay(5000)
            updateSession(sessionId) { s ->
                s.copy(feedingTrough = previousTrough, inventory = previousInventory)
            }
            pendingTroughJobs.remove(key)
        }
    }

    fun removeItemFromFeedingTrough(sessionId: String, itemId: String) {
        val actions = clients[sessionId]?.actions ?: return
        val session = _state.value.sessions.find { it.id == sessionId } ?: return

        // Optimistic: remove crop from trough + add back to inventory
        val previousTrough = session.feedingTrough
        val previousInventory = session.inventory
        val removed = session.feedingTrough.find { it.id == itemId } ?: return
        updateSession(sessionId) { s ->
            s.copy(
                feedingTrough = s.feedingTrough.filter { it.id != itemId },
                inventory = s.inventory.copy(
                    produce = s.inventory.produce + InventoryProduceItem(
                        id = removed.id, species = removed.species,
                        scale = removed.scale, mutations = removed.mutations,
                    )
                ),
            )
        }

        // Send to server
        actions.retrieveItemFromStorage(itemId = itemId, storageId = "FeedingTrough")

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:trough:remove:$itemId"
        pendingTroughJobs[key]?.cancel()
        pendingTroughJobs[key] = viewModelScope.launch {
            delay(5000)
            updateSession(sessionId) { s ->
                s.copy(feedingTrough = previousTrough, inventory = previousInventory)
            }
            pendingTroughJobs.remove(key)
        }
    }

    private val pendingFeedJobs = mutableMapOf<String, Job>()

    fun feedPet(sessionId: String, petItemId: String, cropItemIds: List<String>) {
        val actions = clients[sessionId]?.actions ?: return
        val session = _state.value.sessions.find { it.id == sessionId } ?: return

        // Optimistic: remove crops from inventory
        val previousInventory = session.inventory
        val idsToRemove = cropItemIds.toSet()
        updateSession(sessionId) { s ->
            s.copy(
                inventory = s.inventory.copy(
                    produce = s.inventory.produce.filter { it.id !in idsToRemove }
                ),
            )
        }

        // Send each feed action to server
        cropItemIds.forEach { cropId ->
            actions.feedPet(petItemId = petItemId, cropItemId = cropId)
        }

        // Rollback after 5s if server hasn't confirmed
        val key = "$sessionId:feed:$petItemId:${cropItemIds.first()}"
        pendingFeedJobs[key]?.cancel()
        pendingFeedJobs[key] = viewModelScope.launch {
            delay(5000)
            updateSession(sessionId) { s ->
                s.copy(inventory = previousInventory)
            }
            pendingFeedJobs.remove(key)
        }
    }

    // ---- Pet swap / equip / unequip ----

    /**
     * Swap an active pet with one from inventory or hutch.
     * If the target pet is in the hutch, retrieve it first, then swap, then store the old pet back.
     */
    fun swapPet(sessionId: String, activePetId: String, targetPetId: String, targetIsInHutch: Boolean) {
        val client = clients[sessionId] ?: return
        val actions = client.actions

        if (targetIsInHutch) {
            actions.retrieveItemFromStorage(itemId = targetPetId, storageId = "PetHutch")
        }

        actions.swapPet(petSlotId = activePetId, petInventoryId = targetPetId)

        // Store the old active pet back in hutch
        actions.putItemInStorage(itemId = activePetId, storageId = "PetHutch")
    }

    /**
     * Equip a pet from inventory/hutch into an empty active slot.
     * Uses placePet with a garden dirt tile.
     */
    fun equipPet(sessionId: String, targetPetId: String, targetIsInHutch: Boolean) {
        val client = clients[sessionId] ?: run {
            Log.w(TAG, "[EquipPet] No client for session $sessionId")
            return
        }
        val actions = client.actions

        Log.d(TAG, "[EquipPet] petId=$targetPetId, isInHutch=$targetIsInHutch")

        if (targetIsInHutch) {
            Log.d(TAG, "[EquipPet] Retrieving from hutch first")
            actions.retrieveItemFromStorage(itemId = targetPetId, storageId = "PetHutch")
        }

        // Place pet on a free dirt tile of our garden slot
        val me = client.gameState.getPlayer(client.playerId)
        val slotIndex = (me?.slotIndex ?: 0).coerceIn(0, 5)
        val base = SLOT_BASE_TILE[slotIndex]

        // Find which local indices (0,1,2) are occupied by active pets
        val occupiedLocal = mutableSetOf<Int>()
        val slotInfos = me?.petSlotInfos
        Log.d(TAG, "[EquipPet] petSlotInfos = $slotInfos")
        if (slotInfos != null) {
            for ((_, infoEl) in slotInfos) {
                val pos = (infoEl as? JsonObject)?.get("position") as? JsonObject ?: continue
                val px = pos["x"]?.jsonPrimitive?.intOrNull ?: continue
                // localIndex = px - baseX
                val local = px - base.first
                if (local in 0..2) occupiedLocal.add(local)
            }
        }

        val freeLocal = (0..2).firstOrNull { it !in occupiedLocal } ?: 0
        val x = base.first + freeLocal
        val y = base.second
        Log.d(TAG, "[EquipPet] slotIndex=$slotIndex, occupied=$occupiedLocal, freeLocal=$freeLocal, pos=($x,$y)")
        actions.placePet(
            itemId = targetPetId,
            x = x.toDouble(), y = y.toDouble(),
            tileType = "Dirt",
            localTileIndex = freeLocal,
        )
    }

    /** Base dirt tile (x, y) for each of the 6 player slots. Map is 101 cols, static layout. */
    private val SLOT_BASE_TILE = arrayOf(
        14 to 14,  // slot 0
        40 to 14,  // slot 1
        66 to 14,  // slot 2
        14 to 36,  // slot 3
        40 to 36,  // slot 4
        66 to 36,  // slot 5
    )

    /** Finds a dirt tile in our garden that is not occupied by a pet. */
    private fun findFreeDirtTile(client: RoomClient): DirtTile? {
        val gs = client.gameState.gameState as? JsonObject
        if (gs == null) { Log.w(TAG, "[FindTile] gameState is null"); return null }

        val me = client.gameState.getPlayer(client.playerId)
        if (me == null) { Log.w(TAG, "[FindTile] player not found for ${client.playerId}"); return null }

        val slotIndex = me.slotIndex
        if (slotIndex == null) { Log.w(TAG, "[FindTile] slotIndex is null"); return null }
        Log.d(TAG, "[FindTile] slotIndex=$slotIndex")

        // Read map data — map lives in roomState, not gameState
        val rs = client.gameState.roomState as? JsonObject
        val map = rs?.get("map") as? JsonObject
            ?: gs["map"] as? JsonObject
        if (map == null) {
            Log.w(TAG, "[FindTile] map not found. roomState keys: ${rs?.keys?.take(20)}, gameState keys: ${gs.keys.take(20)}")
            return null
        }

        val cols = map["cols"]?.jsonPrimitive?.intOrNull
        if (cols == null) { Log.w(TAG, "[FindTile] map.cols is null. Map keys: ${map.keys.take(20)}"); return null }

        val dirtArrays = map["userSlotIdxAndDirtTileIdxToGlobalTileIdx"] as? JsonArray
        if (dirtArrays == null) { Log.w(TAG, "[FindTile] dirtArrays is null. Map keys: ${map.keys}"); return null }

        val myDirtTiles = dirtArrays.getOrNull(slotIndex) as? JsonArray
        if (myDirtTiles == null) { Log.w(TAG, "[FindTile] No dirt tiles for slot $slotIndex (array size=${dirtArrays.size})"); return null }
        Log.d(TAG, "[FindTile] Found ${myDirtTiles.size} dirt tiles for slot $slotIndex")

        // Collect positions occupied by active pets
        val occupiedPositions = mutableSetOf<Pair<Int, Int>>()
        val slotInfos = me.petSlotInfos
        Log.d(TAG, "[FindTile] petSlotInfos keys: ${slotInfos?.keys}")
        if (slotInfos != null) {
            for ((key, infoEl) in slotInfos) {
                val info = infoEl as? JsonObject ?: continue
                val pos = info["position"] as? JsonObject ?: continue
                val px = pos["x"]?.jsonPrimitive?.intOrNull ?: continue
                val py = pos["y"]?.jsonPrimitive?.intOrNull ?: continue
                Log.d(TAG, "[FindTile] Pet $key occupies tile ($px, $py)")
                occupiedPositions.add(px to py)
            }
        }

        // Find first dirt tile not occupied by a pet
        for (localIndex in myDirtTiles.indices) {
            val globalIndex = myDirtTiles[localIndex].jsonPrimitive.intOrNull ?: continue
            val x = globalIndex % cols
            val y = globalIndex / cols
            if ((x to y) !in occupiedPositions) {
                Log.d(TAG, "[FindTile] Free tile found: local=$localIndex, global=$globalIndex, pos=($x, $y)")
                return DirtTile(x = x.toDouble(), y = y.toDouble(), localIndex = localIndex)
            }
        }

        // Fallback: use first tile anyway
        Log.w(TAG, "[FindTile] No free tile found, using fallback (first tile)")
        val globalIndex = myDirtTiles.firstOrNull()?.jsonPrimitive?.intOrNull ?: return null
        return DirtTile(
            x = (globalIndex % cols).toDouble(),
            y = (globalIndex / cols).toDouble(),
            localIndex = 0,
        )
    }

    private data class DirtTile(val x: Double, val y: Double, val localIndex: Int)

    /**
     * Remove an active pet (pickup + store in hutch).
     */
    fun unequipPet(sessionId: String, petId: String) {
        val actions = clients[sessionId]?.actions ?: return
        actions.pickupPet(petId = petId)
        actions.putItemInStorage(itemId = petId, storageId = "PetHutch")
    }

    // ---- Card collapse persistence ----

    fun setCardExpanded(key: String, expanded: Boolean) {
        val collapsed = !expanded
        _state.update {
            it.copy(collapsedCards = it.collapsedCards + (key to collapsed))
        }
        viewModelScope.launch { repo.saveCollapsedCards(_state.value.collapsedCards) }
    }

    // ---- Alerts ----

    fun updateAlerts(transform: (AlertConfig) -> AlertConfig) {
        _state.update { it.copy(alerts = transform(it.alerts)) }
        viewModelScope.launch { repo.saveAlerts(_state.value.alerts) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val newSettings = transform(_state.value.settings)
        _state.update { it.copy(settings = newSettings) }
        viewModelScope.launch { repo.saveSettings(newSettings) }
        applySettings(newSettings)
    }

    private fun applySettings(settings: AppSettings) {
        // Update AfkService locks if service is running
        if (serviceRunning) {
            val app = getApplication<Application>()
            val intent = Intent(app, AfkService::class.java)
                .putExtra(AfkService.EXTRA_WIFI_LOCK, settings.wifiLockEnabled)
                .putExtra(AfkService.EXTRA_WAKE_LOCK_MODE, settings.wakeLockMode.toServiceMode())
                .putExtra(AfkService.EXTRA_WAKE_LOCK_DELAY_MIN, settings.wakeLockAutoDelayMin)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }
        // Update reconnect config on all active clients
        val reconnectDelays = com.mgafk.app.data.model.ReconnectDelays(
            supersededMs = settings.retrySupersededDelayMs,
            otherMs = settings.retryDelayMs,
            maxDelayMs = settings.retryMaxDelayMs,
        )
        clients.values.forEach { client ->
            client.reconnectConfig = client.reconnectConfig.copy(delays = reconnectDelays)
        }
    }

    fun testAlert(mode: AlertMode) {
        alertNotifier.testAlert(mode)
    }

    // ---- Preloading ----

    private suspend fun preloadSprites() {
        val app = getApplication<Application>()
        val loader = app.imageLoader
        val categories = listOf(
            "pets" to MgApi.getPets(),
            "plants" to MgApi.getPlants(),
            "items" to MgApi.getItems(),
            "eggs" to MgApi.getEggs(),
            "decors" to MgApi.getDecors(),
            "weathers" to MgApi.getWeathers(),
        )
        categories.forEach { (name, entries) ->
            _state.update { it.copy(loadingStep = "Loading $name sprites… (${entries.size})") }
            entries.values.forEach { entry ->
                val url = entry.sprite ?: return@forEach
                loader.enqueue(ImageRequest.Builder(app).data(url).build())
            }
        }
    }

    // ---- Internal ----

    private fun handleClientEvent(sessionId: String, event: ClientEvent) {
        when (event) {
            is ClientEvent.StatusChanged -> {
                val logDetail = buildString {
                    if (event.code != null) append("code=${event.code}")
                    if (event.message.isNotBlank()) {
                        if (isNotEmpty()) append(" ")
                        append(event.message)
                    }
                    if (event.retry > 0) append(" retry=${event.retry}/${event.maxRetries}")
                    if (event.retryInMs > 0) append(" delay=${event.retryInMs}ms")
                }
                val wsLog = com.mgafk.app.data.model.WsLog(
                    timestamp = System.currentTimeMillis(),
                    level = if (event.status == SessionStatus.ERROR) "error" else "info",
                    event = "status → ${event.status.name}",
                    detail = logDetail,
                )
                val previousSession = _state.value.sessions.find { it.id == sessionId }
                val wasConnected = previousSession?.connected == true
                updateSession(sessionId) {
                    it.copy(
                        status = event.status,
                        connected = event.status == SessionStatus.CONNECTED,
                        busy = event.status == SessionStatus.CONNECTING,
                        error = event.message,
                        playerId = event.playerId.ifBlank { it.playerId },
                        room = event.room.ifBlank { it.room },
                        connectedAt = if (event.status == SessionStatus.CONNECTED) System.currentTimeMillis() else 0,
                        wsLogs = (listOf(wsLog) + it.wsLogs).take(100),
                    )
                }
                // Disconnect / reconnect notifications
                if (_state.value.settings.notifyOnDisconnect && previousSession != null) {
                    val name = previousSession.name
                    if (wasConnected && event.status != SessionStatus.CONNECTED) {
                        alertNotifier.notifyDisconnect(name, event.code, event.message.ifBlank { "Connection lost" })
                    } else if (event.status == SessionStatus.CONNECTED) {
                        alertNotifier.cancelDisconnectNotification(name)
                    }
                }
            }
            is ClientEvent.PlayersChanged -> {
                updateSession(sessionId) { it.copy(players = event.count) }
            }
            is ClientEvent.UptimeChanged -> { /* computed locally in UI */ }
            is ClientEvent.AbilityLogged -> {
                updateSession(sessionId) {
                    val isDuplicate = it.logs.any { existing ->
                        existing.timestamp == event.log.timestamp && existing.action == event.log.action
                    }
                    if (isDuplicate) it
                    else it.copy(logs = (listOf(event.log) + it.logs).take(200))
                }
            }
            is ClientEvent.LiveStatusChanged -> {
                val previousSession = _state.value.sessions.find { it.id == sessionId }
                val previousWeather = previousSession?.weather.orEmpty()
                val newPets = event.pets.map { pet ->
                    PetSnapshot(
                        id = pet.id,
                        name = pet.name,
                        species = pet.species,
                        hunger = pet.hunger,
                        index = pet.index,
                        mutations = pet.mutations,
                        xp = pet.xp,
                        targetScale = pet.targetScale,
                        abilities = pet.abilities,
                    )
                }
                updateSession(sessionId) {
                    it.copy(
                        playerName = event.playerName,
                        roomId = event.roomId,
                        weather = event.weather,
                        pets = newPets,
                    )
                }
                // Fire alert checks (alarm items auto-batch within 300ms)
                val alerts = _state.value.alerts
                alertNotifier.checkWeather(event.weather, previousWeather, alerts)
                alertNotifier.checkPetHunger(newPets, alerts)
            }
            is ClientEvent.GardenChanged -> {
                // Flatten slots: produce one GardenPlantSnapshot per grow slot so multi-harvest
                // plants are tracked per-slot. For slots missing start/end, fall back to
                // tile plantedAt/maturedAt.
                val newGarden = event.plants.flatMap { tile ->
                    val data = tile.data
                    val tileSpecies = data["species"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val plantedAt = data["plantedAt"]?.jsonPrimitive?.longOrNull
                    val maturedAt = data["maturedAt"]?.jsonPrimitive?.longOrNull
                    val slots = data["slots"] as? JsonArray

                    // If no explicit slots, create a single snapshot using plantedAt/maturedAt
                    if (slots == null || slots.isEmpty()) {
                        val muts = mutableSetOf<String>()
                        val maxScale = 0.0
                        listOf(GardenPlantSnapshot(
                            tileId = tile.tileId,
                            species = tileSpecies,
                            targetScale = maxScale,
                            mutations = muts.toList(),
                            startTime = plantedAt,
                            endTime = maturedAt,
                            slotId = null,
                            isTileLevel = true,
                        ))
                    } else {
                        // If the tile-level maturedAt is in the future (plant not yet matured),
                        // show a single tile-level snapshot (don't split into slot snapshots yet).
                        val now = System.currentTimeMillis()
                        if (maturedAt != null && maturedAt > now) {
                            // Aggregate tile snapshot using plantedAt/maturedAt
                            val muts = mutableSetOf<String>()
                            var maxScale = 0.0
                            slots.forEach { slotEl ->
                                val slot = slotEl as? JsonObject ?: return@forEach
                                val scale = slot["targetScale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                                if (scale > maxScale) maxScale = scale
                                (slot["mutations"] as? JsonArray)?.forEach { m ->
                                    val name = m.jsonPrimitive.contentOrNull
                                    if (!name.isNullOrBlank()) muts.add(name)
                                }
                            }
                            listOf(GardenPlantSnapshot(
                                tileId = tile.tileId,
                                species = tileSpecies,
                                targetScale = maxScale,
                                mutations = muts.toList(),
                                startTime = plantedAt,
                                endTime = maturedAt,
                                slotId = null,
                                isTileLevel = true,
                            ))
                        } else {
                            // Build a snapshot per slot
                            slots.mapNotNull { slotEl ->
                                val slot = slotEl as? JsonObject ?: return@mapNotNull null
                                val slotSpecies = slot["species"]?.jsonPrimitive?.contentOrNull ?: tileSpecies
                                val slotStart = slot["startTime"]?.jsonPrimitive?.longOrNull ?: plantedAt
                                val slotEnd = slot["endTime"]?.jsonPrimitive?.longOrNull ?: maturedAt
                                val slotScale = slot["targetScale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                                val slotMutations = (slot["mutations"] as? JsonArray)
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                    ?.filter { it.isNotBlank() } ?: emptyList()
                                val sid = slot["slotId"]?.jsonPrimitive?.intOrNull

                                GardenPlantSnapshot(
                                    tileId = tile.tileId,
                                    species = slotSpecies,
                                    targetScale = slotScale,
                                    mutations = slotMutations,
                                    startTime = slotStart,
                                    endTime = slotEnd,
                                    slotId = sid,
                                )
                            }
                        }
                    }
                }

                updateSession(sessionId) { it.copy(garden = newGarden) }
            }
            is ClientEvent.InventoryChanged -> {
                val seeds = mutableListOf<InventorySeedItem>()
                val eggs = mutableListOf<InventoryEggItem>()
                val produce = mutableListOf<InventoryProduceItem>()
                val plants = mutableListOf<InventoryPlantItem>()
                val pets = mutableListOf<InventoryPetItem>()
                val tools = mutableListOf<InventoryToolItem>()
                val decors = mutableListOf<InventoryDecorItem>()

                for (el in event.items) {
                    val obj = el as? JsonObject ?: continue
                    when (obj["itemType"]?.jsonPrimitive?.contentOrNull) {
                        "Seed" -> seeds.add(InventorySeedItem(
                            species = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                        ))
                        "Egg" -> eggs.add(InventoryEggItem(
                            eggId = obj["eggId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                        ))
                        "Produce" -> produce.add(InventoryProduceItem(
                            id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            species = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            scale = obj["scale"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                            mutations = (obj["mutations"] as? JsonArray)
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                ?.filter { it.isNotBlank() } ?: emptyList(),
                        ))
                        "Plant" -> {
                            val slots = obj["slots"] as? JsonArray
                            val plantSpecies = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            var plantPrice = 0L
                            slots?.forEach { slotEl ->
                                val slot = slotEl as? JsonObject ?: return@forEach
                                val slotSpecies = slot["species"]?.jsonPrimitive?.contentOrNull
                                    ?: plantSpecies
                                val scale = slot["targetScale"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                                val muts = (slot["mutations"] as? JsonArray)
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                    ?.filter { it.isNotBlank() } ?: emptyList()
                                plantPrice += PriceCalculator.calculateCropSellPrice(slotSpecies, scale, muts) ?: 0L
                            }
                            plants.add(InventoryPlantItem(
                                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                species = plantSpecies,
                                growSlots = slots?.size ?: 0,
                                totalPrice = plantPrice,
                            ))
                        }
                        "Pet" -> pets.add(InventoryPetItem(
                            id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            petSpecies = obj["petSpecies"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            name = obj["name"]?.jsonPrimitive?.contentOrNull,
                            xp = obj["xp"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                            targetScale = obj["targetScale"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                            mutations = (obj["mutations"] as? JsonArray)
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                ?.filter { it.isNotBlank() } ?: emptyList(),
                            abilities = (obj["abilities"] as? JsonArray)
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                        ))
                        "Tool" -> tools.add(InventoryToolItem(
                            toolId = obj["toolId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                        ))
                        "Decor" -> decors.add(InventoryDecorItem(
                            decorId = obj["decorId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                        ))
                    }
                }
                // Parse storages separately
                val siloSeeds = mutableListOf<InventorySeedItem>()
                val shedDecors = mutableListOf<InventoryDecorItem>()
                val hutchPets = mutableListOf<InventoryPetItem>()
                val troughCrops = mutableListOf<InventoryCropsItem>()

                for (storageEl in event.storages) {
                    val storage = storageEl as? JsonObject ?: continue
                    val storageId = storage["decorId"]?.jsonPrimitive?.contentOrNull ?: continue
                    val storageItems = storage["items"] as? JsonArray ?: continue
                    for (el in storageItems) {
                        val obj = el as? JsonObject ?: continue
                        when (storageId) {
                            "SeedSilo" -> siloSeeds.add(InventorySeedItem(
                                species = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                            ))
                            "DecorShed" -> shedDecors.add(InventoryDecorItem(
                                decorId = obj["decorId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                            ))
                            "PetHutch" -> hutchPets.add(InventoryPetItem(
                                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                petSpecies = obj["petSpecies"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                name = obj["name"]?.jsonPrimitive?.contentOrNull,
                                xp = obj["xp"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                                targetScale = obj["targetScale"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                                mutations = (obj["mutations"] as? JsonArray)
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                    ?.filter { it.isNotBlank() } ?: emptyList(),
                                abilities = (obj["abilities"] as? JsonArray)
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                            ))
                            "FeedingTrough" -> troughCrops.add(InventoryCropsItem(
                                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                species = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                scale = obj["scale"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                                mutations = (obj["mutations"] as? JsonArray)
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                    ?.filter { it.isNotBlank() } ?: emptyList(),
                            ))
                        }
                    }
                }
                // Server confirmed — cancel any pending rollbacks
                pendingTroughJobs.values.forEach { it.cancel() }
                pendingTroughJobs.clear()
                pendingFeedJobs.values.forEach { it.cancel() }
                pendingFeedJobs.clear()

                updateSession(sessionId) {
                    it.copy(
                        inventory = InventorySnapshot(seeds, eggs, produce, plants, pets, tools, decors),
                        seedSilo = siloSeeds,
                        decorShed = shedDecors,
                        petHutch = hutchPets,
                        feedingTrough = troughCrops,
                    )
                }
                alertNotifier.checkFeedingTrough(troughCrops, _state.value.alerts)
            }
            is ClientEvent.EggsChanged -> {
                val newEggs = event.eggs.map { tile ->
                    val data = tile.data
                    GardenEggSnapshot(
                        tileId = tile.tileId,
                        eggId = data["eggId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        plantedAt = data["plantedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                        maturedAt = data["maturedAt"]?.jsonPrimitive?.longOrNull ?: 0L,
                    )
                }
                updateSession(sessionId) { it.copy(gardenEggs = newEggs) }
            }
            is ClientEvent.ShopsChanged -> {
                val previousShops = _state.value.sessions.find { it.id == sessionId }?.shops.orEmpty()
                val purchases = event.shopPurchases
                val newShops = event.shops.map { shop ->
                    val initialStocks = shop.getItemStocks()
                    // Detect shop restock: if the timer went UP, the shop just restocked
                    // and shopPurchases may be stale (reset arrives in a separate patch)
                    val prevShop = previousShops.find { it.type == shop.type }
                    val justRestocked = prevShop != null &&
                        shop.secondsUntilRestock > (prevShop.secondsUntilRestock + 30)
                    val purchaseMap = if (justRestocked) null else purchases
                        ?.get(shop.type)
                        ?.let { it as? JsonObject }
                        ?.get("purchases")
                        ?.let { it as? JsonObject }
                    val remainingStocks = initialStocks.mapValues { (name, initial) ->
                        val bought = purchaseMap?.get(name)?.jsonPrimitive?.intOrNull ?: 0
                        maxOf(0, initial - bought)
                    }
                    ShopSnapshot(
                        type = shop.type,
                        itemNames = shop.getItemNames(),
                        itemStocks = remainingStocks,
                        initialStocks = initialStocks,
                        secondsUntilRestock = shop.secondsUntilRestock,
                    )
                }
                // Server confirmed — cancel any pending rollback jobs for this session
                pendingPurchaseJobs.keys.filter { it.startsWith("$sessionId:") }.forEach { key ->
                    pendingPurchaseJobs.remove(key)?.cancel()
                }
                updateSession(sessionId) { it.copy(shops = newShops) }
                // Only check alerts when actual items changed, not just the restock timer
                val oldItems = previousShops.associate { it.type to it.itemNames }
                val newItems = newShops.associate { it.type to it.itemNames }
                if (oldItems != newItems) {
                    alertNotifier.checkShopItems(newShops, _state.value.alerts)
                }
            }
            is ClientEvent.ChatChanged -> {
                updateSession(sessionId) { it.copy(chatMessages = event.messages) }
            }
            is ClientEvent.PlayersListChanged -> {
                updateSession(sessionId) { it.copy(playersList = event.players) }
            }
            is ClientEvent.DebugLog -> {
                updateSession(sessionId) {
                    val entry = com.mgafk.app.data.model.WsLog(
                        timestamp = System.currentTimeMillis(),
                        level = event.level,
                        event = event.message,
                        detail = event.detail,
                    )
                    it.copy(wsLogs = (listOf(entry) + it.wsLogs).take(100))
                }
            }
        }
    }

    private fun persist() {
        viewModelScope.launch {
            repo.saveSessions(_state.value.sessions)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (_: Exception) {}
        collectorJobs.values.forEach { it.cancel() }
        collectorJobs.clear()
        clients.values.forEach { it.dispose() }
        clients.clear()
        alertNotifier.cleanup()
        // Stop service if running
        if (serviceRunning) {
            val app = getApplication<Application>()
            app.stopService(Intent(app, AfkService::class.java))
            serviceRunning = false
        }
    }
}
