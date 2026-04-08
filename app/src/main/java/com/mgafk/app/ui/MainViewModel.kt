package com.mgafk.app.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import coil.imageLoader
import coil.request.ImageRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.AlertMode
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.model.ReconnectConfig
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.data.model.ShopSnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.SessionRepository
import com.mgafk.app.data.repository.VersionFetcher
import com.mgafk.app.data.websocket.ClientEvent
import com.mgafk.app.data.websocket.RoomClient
import com.mgafk.app.service.AfkService
import com.mgafk.app.service.AlertNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val sessions: List<Session> = listOf(Session()),
    val activeSessionId: String = "",
    val alerts: AlertConfig = AlertConfig(),
    val connecting: Boolean = false,
    val apiReady: Boolean = false,
    val loadingStep: String = "",
) {
    val activeSession: Session
        get() = sessions.find { it.id == activeSessionId } ?: sessions.first()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SessionRepository(application)
    private val alertNotifier = AlertNotifier(application)
    private val clients = mutableMapOf<String, RoomClient>()
    private val collectorJobs = mutableMapOf<String, Job>()
    private var serviceRunning = false

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val sessions = repo.loadSessions().ifEmpty { listOf(Session()) }
            val activeId = repo.loadActiveSessionId() ?: sessions.first().id
            val alerts = repo.loadAlerts()
            _state.value = UiState(
                sessions = sessions,
                activeSessionId = activeId,
                alerts = alerts,
            )
            // Preload ALL API data + sprites at startup
            launch {
                _state.update { it.copy(loadingStep = "Loading game data…") }
                MgApi.preloadAll()
                _state.update { it.copy(loadingStep = "Preloading sprites…") }
                preloadSprites()
                _state.update { it.copy(apiReady = true, loadingStep = "") }
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

                client.connect(
                    version = version,
                    cookie = session.cookie,
                    room = session.room,
                    host = host,
                    reconnect = session.reconnect,
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

    // ---- Alerts ----

    fun updateAlerts(transform: (AlertConfig) -> AlertConfig) {
        _state.update { it.copy(alerts = transform(it.alerts)) }
        viewModelScope.launch { repo.saveAlerts(_state.value.alerts) }
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
                updateSession(sessionId) {
                    it.copy(
                        status = event.status,
                        connected = event.status == SessionStatus.CONNECTED,
                        busy = event.status == SessionStatus.CONNECTING,
                        error = event.message,
                        playerId = event.playerId.ifBlank { it.playerId },
                        room = event.room.ifBlank { it.room },
                        connectedAt = if (event.status == SessionStatus.CONNECTED) System.currentTimeMillis() else 0,
                    )
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
            is ClientEvent.ShopsChanged -> {
                val previousShops = _state.value.sessions.find { it.id == sessionId }?.shops.orEmpty()
                val newShops = event.shops.map { shop ->
                    ShopSnapshot(
                        type = shop.type,
                        itemNames = shop.getItemNames(),
                        itemStocks = shop.getItemStocks(),
                        secondsUntilRestock = shop.secondsUntilRestock,
                    )
                }
                updateSession(sessionId) { it.copy(shops = newShops) }
                // Only check alerts when actual items changed, not just the restock timer
                val oldItems = previousShops.associate { it.type to it.itemNames }
                val newItems = newShops.associate { it.type to it.itemNames }
                if (oldItems != newItems) {
                    alertNotifier.checkShopItems(newShops, _state.value.alerts)
                }
            }
            is ClientEvent.DebugLog -> { /* Could be stored for dev tools */ }
        }
    }

    private fun persist() {
        viewModelScope.launch {
            repo.saveSessions(_state.value.sessions)
        }
    }

    override fun onCleared() {
        super.onCleared()
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
