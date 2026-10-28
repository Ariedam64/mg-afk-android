package com.mgafk.app.ui

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.mgafk.app.data.AppLog
import coil.imageLoader
import coil.request.ImageRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.AlertMode
import com.mgafk.app.data.model.AppSettings
import com.mgafk.app.data.model.Appearance
import com.mgafk.app.data.repository.CropSize
import com.mgafk.app.data.repository.PetTeams
import com.mgafk.app.data.repository.GardenTiles
import com.mgafk.app.data.model.BotSnapshot
import com.mgafk.app.data.model.BotStatus
import com.mgafk.app.data.websocket.BotClient
import com.mgafk.app.data.websocket.BotEvent
import com.mgafk.app.data.websocket.BotNameGenerator
import com.mgafk.app.data.websocket.IdGenerator
import com.mgafk.app.data.model.WakeLockMode
import java.util.UUID
import com.mgafk.app.data.model.ChatMessage
import com.mgafk.app.data.model.PlayerSnapshot
import com.mgafk.app.data.model.GardenEggSnapshot
import com.mgafk.app.data.model.GardenPlantSnapshot
import com.mgafk.app.data.model.InventoryEggItem
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventoryPlantItem
import com.mgafk.app.data.model.CrystalType
import com.mgafk.app.data.model.GardenTileType
import com.mgafk.app.data.model.PlacedCrystal
import com.mgafk.app.data.repository.Crystals
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.model.InventorySnapshot
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.model.POTION_STORAGE_ID
import com.mgafk.app.data.model.REPLENISH_POTION_ID
import com.mgafk.app.data.model.XP_POTION_ID
import com.mgafk.app.data.model.InventoryCropsItem
import com.mgafk.app.data.model.InventoryDecorItem
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.model.PetTeam
import com.mgafk.app.data.model.ReconnectConfig
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.model.WeatherForecast
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.data.model.ShopSnapshot
import com.mgafk.app.data.model.Tip
import com.mgafk.app.data.repository.AriesApi
import com.mgafk.app.data.repository.BugReportConnection
import com.mgafk.app.data.repository.BugReportContext
import com.mgafk.app.data.repository.BugReportSnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.SessionRepository
import com.mgafk.app.data.repository.StateCollector
import com.mgafk.app.data.repository.AppRelease
import com.mgafk.app.data.repository.VersionFetcher
import com.mgafk.app.data.websocket.ClientEvent
import com.mgafk.app.data.websocket.RoomClient
import com.mgafk.app.service.AfkService
import com.mgafk.app.service.AfkWatchdogWorker
import com.mgafk.app.service.AlertNotifier
import com.mgafk.app.service.AutoStockTracker
import com.mgafk.app.service.cancelResumeNotification
import com.mgafk.app.ui.actions.GardenActions
import com.mgafk.app.ui.actions.PetActions
import com.mgafk.app.ui.actions.RoomActions
import com.mgafk.app.ui.actions.SessionContext
import com.mgafk.app.ui.actions.ShopActions
import com.mgafk.app.ui.actions.StorageActions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun WakeLockMode.toServiceMode(): Int = when (this) {
    WakeLockMode.OFF -> AfkService.MODE_OFF
    WakeLockMode.SMART -> AfkService.MODE_SMART
    WakeLockMode.ALWAYS -> AfkService.MODE_ALWAYS
}

/** How often the Weather Station forecast is refetched. The card ticks its own countdowns. */
private const val WEATHER_STATION_REFRESH_MS = 60_000L

data class UiState(
    val sessions: List<Session> = listOf(Session()),
    val activeSessionId: String = "",
    val alerts: AlertConfig = AlertConfig(),
    val collapsedCards: Map<String, Boolean> = emptyMap(),
    val connecting: Boolean = false,
    val apiReady: Boolean = false,
    val weatherForecast: WeatherForecast? = null,
    val loadingStep: String = "",
    val updateAvailable: AppRelease? = null,
    val purchaseError: String = "",
    /** Tips still on screen. Empty until the dismissed ones are read from disk. */
    val visibleTips: Set<Tip> = emptySet(),
    val settings: AppSettings = AppSettings(),
    val serviceLogs: List<AfkService.ServiceLog> = emptyList(),
    val currencyBalance: Long? = null,
    val currencyBalanceLoading: Boolean = false,
    val currencyBalanceError: String? = null,
    val publicRooms: List<AriesApi.PublicRoom> = emptyList(),
    val publicRoomsLoading: Boolean = false,
) {
    val activeSession: Session
        get() = sessions.find { it.id == activeSessionId } ?: sessions.first()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "MainViewModel"
        /** How often each connected session is evaluated for a collect-state send. */
        const val STATE_COLLECT_INTERVAL_MS = 60_000L
        /**
         * On connect the userSlot is hydrated shortly after the room player
         * resolves, so the immediate collect-state is retried briefly until the
         * slot loads (else we'd just skip it and wait for the next minute tick).
         */
        const val ON_CONNECT_COLLECT_ATTEMPTS = 20
        const val ON_CONNECT_COLLECT_RETRY_MS = 1_500L
        /** A shop deadline may shift later by a hair on its own; only a jump past this is a restock. */
        const val RESTOCK_COUNTDOWN_JITTER_SEC = 30
    }
    private val repo = SessionRepository(application)
    private val alertNotifier = AlertNotifier(application)
    private val clients = mutableMapOf<String, RoomClient>()
    private val collectorJobs = mutableMapOf<String, Job>()
    /** Reports player state to the backend (online flag + data sync). */
    private val stateCollector = StateCollector()
    private var serviceRunning = false
    private val connectivityManager =
        application.getSystemService(ConnectivityManager::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Network just came back - force immediate retry on all reconnecting clients
            clients.forEach { (_, client) -> client.retryNow() }
        }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val ctx = SessionContext(
        app = application,
        scope = viewModelScope,
        state = _state,
        clients = clients,
        repo = repo,
        alertNotifier = alertNotifier,
    )

    val shops = ShopActions(ctx)
    val room = RoomActions(ctx, connect = ::connect, disconnect = ::disconnect)
    val pets = PetActions(ctx)
    val garden = GardenActions(ctx)
    val storage = StorageActions(ctx)

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
            val settings = repo.loadSettings()
            alertNotifier.alarmSoundUri = settings.alarmSoundUri
            alertNotifier.alarmSchedules = settings.alarmSchedules
            alertNotifier.alarmVolume = settings.alarmVolume
            // Legacy migration: pet teams used to be a single global list. Seed each
            // session that has none yet from the old global one, so the previous behaviour
            // (same teams on every session) is preserved.
            // Pet teams are server state now (see PetTeams); nothing is seeded from disk.
            val migratedSessions = sessions
            val dismissedTips = repo.loadDismissedTips()
            _state.value = UiState(
                sessions = migratedSessions,
                activeSessionId = activeId,
                alerts = alerts,
                collapsedCards = collapsedCards,
                visibleTips = Tip.entries.toSet() - dismissedTips,
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
                startWeatherStationRefresh()
                _state.update { it.copy(loadingStep = "Preloading sprites…") }
                preloadSprites()
                _state.update { it.copy(apiReady = true, loadingStep = "") }
                // Auto-reconnect sessions that were live before the process
                // died. wantConnected survives serialization (unlike connected
                // /status which are stripped on save), so we use it as the
                // "user wants this session live" flag.
                autoReconnectPendingSessions()
            }
            // Check for app updates periodically (immediately + every hour)
            launch {
                while (true) {
                    checkForUpdate()
                    delay(3_600_000) // 1 hour
                }
            }
            // Report player state to the backend once a minute. Each connected
            // session sends only when its data changed, or as a 5-min keep-alive.
            launch {
                while (true) {
                    delay(STATE_COLLECT_INTERVAL_MS)
                    collectAllSessionStates()
                }
            }
        }
    }

    // ---- Update check ----

    private suspend fun checkForUpdate() {
        try {
            val release = VersionFetcher.fetchLatestRelease() ?: return
            val current = com.mgafk.app.BuildConfig.VERSION_NAME
            if (!VersionFetcher.isNewer(current, release.tagName)) return

            _state.update { it.copy(updateAvailable = release) }

            // Only send notification once per version
            val lastNotified = repo.getLastNotifiedVersion()
            if (lastNotified != release.tagName) {
                alertNotifier.notifyUpdate(release.tagName, release.downloadUrl)
                repo.setLastNotifiedVersion(release.tagName)
            }
        } catch (_: Exception) {
            // Silent - don't crash the app over an update check
        }
    }

    // ---- Session Management ----

    fun addSession() {
        _state.update { s ->
            val newSession = Session(name = "Session ${s.sessions.size + 1}")
            s.copy(sessions = s.sessions + newSession, activeSessionId = newSession.id)
        }
        ctx.persist()
    }

    fun removeSession(id: String) {
        collectorJobs.remove(id)?.cancel()
        stateCollector.reset(id)
        val client = clients.remove(id)
        client?.dispose()
        _state.update { s ->
            val filtered = s.sessions.filter { it.id != id }
            val sessions = filtered.ifEmpty { listOf(Session()) }
            val activeId = if (s.activeSessionId == id) sessions.first().id else s.activeSessionId
            s.copy(sessions = sessions, activeSessionId = activeId)
        }
        ctx.persist()
    }

    fun selectSession(id: String) {
        _state.update { it.copy(activeSessionId = id) }
        viewModelScope.launch { repo.saveActiveSessionId(id) }
    }

    /** Applies [transform] to one session and saves the sessions, see [SessionContext.updateSession]. */
    fun updateSession(id: String, transform: (Session) -> Session) {
        ctx.updateSession(id, transform)
    }

    // ---- Connection ----

    /**
     * Reconnect any session that was live before the process died
     * (wantConnected=true). Called once after API + sprites are ready so the
     * WS handshake doesn't race with version lookups. connect() returns
     * immediately and runs the WS work inside viewModelScope, so launching
     * them all back-to-back is safe - server-side rate-limit fallback is
     * handled by [ReconnectConfig] anyway.
     */
    private fun autoReconnectPendingSessions() {
        val pending = _state.value.sessions.filter { it.wantConnected && it.cookie.isNotBlank() }
        if (pending.isEmpty()) return
        AppLog.d(TAG, "[AutoReconnect] resuming ${pending.size} session(s)")
        for (session in pending) connect(session.id)
    }

    private fun startAfkService() {
        // Watchdog is independent of the in-memory serviceRunning flag -
        // scheduling is idempotent (KEEP policy) so it's safe to call every time.
        // If the start below is refused it finds no service and posts the "tap to resume"
        // notification, which brings the app back on screen where the start is retried.
        AfkWatchdogWorker.schedule(getApplication())
        if (serviceRunning) return
        // A refusal means the app is in the background. The session still connects (its
        // socket lives in this process), and ensureAfkService() tries again once the app
        // is back on screen.
        serviceRunning = sendToAfkService(_state.value.settings, asForeground = true)
    }

    /**
     * Starts AfkService with [settings], or hands them to the running one. Returns false
     * when Android refuses.
     *
     * Android 12+ refuses a foreground-service start from the background with
     * ForegroundServiceStartNotAllowedException, and a plain start is refused there too
     * unless a foreground service already runs. Both are IllegalStateExceptions. Some callers
     * are coroutines that resume long after the player left the app, such as the
     * auto-reconnect that waits for the game data and sprites to load, and an uncaught
     * refusal there took the whole app down.
     */
    private fun sendToAfkService(settings: AppSettings, asForeground: Boolean): Boolean {
        val app = getApplication<Application>()
        val intent = Intent(app, AfkService::class.java)
            .putExtra(AfkService.EXTRA_WIFI_LOCK, settings.wifiLockEnabled)
            .putExtra(AfkService.EXTRA_WAKE_LOCK_MODE, settings.wakeLockMode.toServiceMode())
            .putExtra(AfkService.EXTRA_WAKE_LOCK_DELAY_MIN, settings.wakeLockAutoDelayMin)
        return try {
            if (asForeground) app.startForegroundService(intent) else app.startService(intent)
            true
        } catch (e: IllegalStateException) {
            AppLog.w(TAG, "[Service] start refused, app in background: ${e.message}")
            false
        }
    }

    /**
     * Starts the foreground service if a session needs it and it is not running. Called when
     * the app comes back on screen, where Android allows the start, so a start refused in the
     * background does not leave the session without it for good.
     *
     * A session that wants to connect but has no cookie is left out, as auto-reconnect leaves
     * it out: it can never connect, and the service would run for nothing.
     */
    fun ensureAfkService() {
        val needed = _state.value.sessions.any {
            it.connected || (it.wantConnected && it.cookie.isNotBlank())
        }
        if (needed) startAfkService()
    }

    private fun stopAfkServiceIfIdle() {
        val anyConnected = _state.value.sessions.any { it.connected }
        if (!anyConnected && serviceRunning) {
            val app = getApplication<Application>()
            app.stopService(Intent(app, AfkService::class.java))
            // Cancel the watchdog too - no live sessions, nothing to babysit.
            AfkWatchdogWorker.cancel(app)
            serviceRunning = false
        }
    }

    fun connect(sessionId: String) {
        val session = _state.value.sessions.find { it.id == sessionId } ?: return
        if (session.cookie.isBlank()) return

        startAfkService()
        updateSession(sessionId) { it.copy(busy = true, status = SessionStatus.CONNECTING, wantConnected = true) }

        viewModelScope.launch {
            try {
                val version = VersionFetcher.fetchVersionForRoom(
                    host = session.gameUrl.removePrefix("https://").removePrefix("http://").ifBlank { "magicgarden.gg" },
                    room = session.room,
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
                    versionFetcher = { VersionFetcher.fetchVersionForRoom(host = host, room = session.room) },
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
        disconnectInternal(sessionId, stopServiceIfIdle = true)
    }

    /**
     * Disconnect a session without stopping the foreground service even if
     * no other sessions remain connected. Used when launching [PlayActivity]
     * so the notification stays visible and the OS doesn't kill the process
     * - restarting an FGS after returning from another activity is unreliable
     * on Android 14+ due to background-start restrictions.
     */
    fun disconnectKeepService(sessionId: String) {
        disconnectInternal(sessionId, stopServiceIfIdle = false)
    }

    private fun disconnectInternal(sessionId: String, stopServiceIfIdle: Boolean) {
        collectorJobs.remove(sessionId)?.cancel()
        // A reconnect rebuilds the inventory from scratch, so a move that was stuck before
        // deserves another go.
        storage.forgetAutoStock(sessionId)
        stateCollector.reset(sessionId)
        clients[sessionId]?.disconnect()
        // Bots are tied to the parent session - kill them when the user disconnects.
        room.disconnectAllBots(sessionId)
        // Only clear wantConnected on an explicit user disconnect (stopServiceIfIdle=true).
        // For the PlayActivity flow (keepService=false) we want the auto-resume
        // path to fire if the process dies while the game is open.
        updateSession(sessionId) {
            it.copy(
                connected = false,
                busy = false,
                status = SessionStatus.IDLE,
                players = 0,
                connectedAt = 0,
                hostPlayerId = "",
                wantConnected = if (stopServiceIfIdle) false else it.wantConnected,
            )
        }
        if (stopServiceIfIdle) stopAfkServiceIfIdle()
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

    /** Hides [tip] now and remembers it, so it stays hidden on the next launch. */
    fun dismissTip(tip: Tip) {
        _state.update { it.copy(visibleTips = it.visibleTips - tip) }
        viewModelScope.launch { repo.dismissTip(tip) }
    }

    // ---- Weather Station ----

    private var weatherStationJob: Job? = null

    /**
     * Keeps [UiState.weatherForecast] fresh. The card ticks its own countdowns from the events'
     * timestamps, so this only has to run often enough to pick up the next events, not to drive
     * the display.
     */
    private fun startWeatherStationRefresh() {
        if (weatherStationJob?.isActive == true) return
        weatherStationJob = viewModelScope.launch {
            while (true) {
                VersionFetcher.fetchGameWeather()?.let { forecast ->
                    _state.update { it.copy(weatherForecast = forecast) }
                }
                delay(WEATHER_STATION_REFRESH_MS)
            }
        }
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

    private var saveAppearanceJob: Job? = null

    /**
     * Changes how the app looks. Applied at once, saved a moment later: the hue slider calls
     * this on every frame of a drag, and only where it stops needs to reach the disk.
     */
    fun updateAppearance(transform: (Appearance) -> Appearance) {
        _state.update { it.copy(settings = it.settings.copy(appearance = transform(it.settings.appearance))) }
        saveAppearanceJob?.cancel()
        saveAppearanceJob = viewModelScope.launch {
            delay(400)
            repo.saveSettings(_state.value.settings)
        }
    }

    private fun applySettings(settings: AppSettings) {
        // Push the chosen alarm sound URI to the notifier so the next alarm uses it.
        alertNotifier.alarmSoundUri = settings.alarmSoundUri
        alertNotifier.alarmSchedules = settings.alarmSchedules
        alertNotifier.alarmVolume = settings.alarmVolume
        // Update AfkService locks if service is running
        if (serviceRunning) {
            // The service is already in the foreground, so a plain start is enough to hand it
            // the new settings, and it is allowed even from the background.
            if (!sendToAfkService(settings, asForeground = false)) serviceRunning = false
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

    /** Plays the configured alarm sound at the configured volume (preview from Settings). */
    fun previewAlarmSound() {
        alertNotifier.previewAlarmSound()
    }

    /** Stops the volume preview. */
    fun stopPreviewAlarmSound() {
        alertNotifier.stopPreviewSound()
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
                if (event.status == SessionStatus.CONNECTED) {
                    // A session is live again (user tap, auto-reconnect, or WS
                    // self-reconnect): clear any stale "tap to resume" notif.
                    cancelResumeNotification(getApplication<Application>())
                    // Report online immediately rather than waiting for the
                    // next minute tick, so the backend marks the player online.
                    collectStateNow(sessionId)
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
                val current = _state.value.sessions.find { it.id == sessionId }
                val isDuplicate = current?.logs?.any { existing ->
                    existing.timestamp == event.log.timestamp && existing.action == event.log.action
                } == true
                if (!isDuplicate) {
                    updateSession(sessionId) {
                        it.copy(logs = (listOf(event.log) + it.logs).take(200))
                    }
                    // Notify if this ability proc is enabled in the alert config.
                    alertNotifier.checkAbilityProc(event.log, _state.value.alerts)
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
                        hostPlayerId = event.hostPlayerId,
                        weather = event.weather,
                        pets = newPets,
                    )
                }
                // Fire alert checks (alarm items auto-batch within 300ms)
                val alerts = _state.value.alerts
                alertNotifier.checkWeather(event.weather, previousWeather, alerts)
                alertNotifier.checkPetHunger(sessionId, newPets, alerts)
            }
            is ClientEvent.PetTeamsChanged -> {
                updateSession(sessionId) { it.copy(petTeams = event.teams) }
            }
            is ClientEvent.GardenChanged -> {
                val newGarden = mutableListOf<GardenPlantSnapshot>()
                for (tile in event.plants) {
                    val data = tile.data
                    val slots = data["slots"] as? JsonArray ?: continue
                    for ((index, slotEl) in slots.withIndex()) {
                        val slot = slotEl as? JsonObject ?: continue
                        // Prefer the slot's own slotId - that's what the server
                        // expects back in HarvestCrop.slotsIndex and
                        // CropCleanser.growSlotIdx. Fall back to the array
                        // position only if the field is missing.
                        val slotId = slot["slotId"]?.jsonPrimitive?.intOrNull ?: index
                        val species = slot["species"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val mutations = mutableListOf<String>()
                        (slot["mutations"] as? JsonArray)?.forEach { m ->
                            val name = m.jsonPrimitive.contentOrNull
                            if (!name.isNullOrBlank()) mutations.add(name)
                        }
                        newGarden += GardenPlantSnapshot(
                            tileId = tile.tileId,
                            slotIndex = slotId,
                            species = species,
                            size = CropSize.clamp(slot["size"]?.jsonPrimitive?.doubleOrNull ?: 0.0),
                            mutations = mutations,
                            startTime = slot["startTime"]?.jsonPrimitive?.longOrNull ?: 0L,
                            endTime = slot["endTime"]?.jsonPrimitive?.longOrNull ?: 0L,
                            preserved = slot["preserved"]?.jsonPrimitive?.booleanOrNull == true,
                        )
                    }
                }
                // Server confirmed garden change - cancel pending plant/water rollbacks
                garden.onGardenConfirmed(sessionId)
                val freeTiles = clients[sessionId]?.let { garden.computeFreePlantTileCount(it) } ?: 0
                updateSession(sessionId) { it.copy(garden = newGarden, freePlantTiles = freeTiles) }
            }
            is ClientEvent.CrystalsChanged -> {
                // Not persisted: the server reports it again on every reconnect, and it changes
                // as often as a crystal burns down.
                ctx.updateSessionInMemory(sessionId) {
                    it.copy(
                        crystals = event.crystals,
                        occupiedTiles = event.occupiedTiles,
                        crystalsReadAtMs = System.currentTimeMillis(),
                    )
                }
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
                            size = CropSize.clamp(obj["size"]?.jsonPrimitive?.doubleOrNull ?: 0.0),
                            mutations = (obj["mutations"] as? JsonArray)
                                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                ?.filter { it.isNotBlank() } ?: emptyList(),
                        ))
                        "Plant" -> {
                            val slotsArray = obj["slots"] as? JsonArray
                            val plantSpecies = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            var plantPrice = 0L
                            val parsedSlots = mutableListOf<com.mgafk.app.data.model.InventoryPlantSlot>()
                            slotsArray?.forEach { slotEl ->
                                val slot = slotEl as? JsonObject ?: return@forEach
                                val slotSpecies = slot["species"]?.jsonPrimitive?.contentOrNull
                                    ?: plantSpecies
                                val size = CropSize.clamp(slot["size"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
                                val muts = (slot["mutations"] as? JsonArray)
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                    ?.filter { it.isNotBlank() } ?: emptyList()
                                plantPrice += PriceCalculator.calculateCropSellPrice(slotSpecies, size, muts) ?: 0L
                                parsedSlots.add(com.mgafk.app.data.model.InventoryPlantSlot(
                                    species = slotSpecies,
                                    size = size,
                                    mutations = muts,
                                ))
                            }
                            plants.add(InventoryPlantItem(
                                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                species = plantSpecies,
                                growSlots = slotsArray?.size ?: 0,
                                totalPrice = plantPrice,
                                slots = parsedSlots,
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
                            sourceEggId = obj["sourceEggId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        ))
                        "Tool" -> tools.add(InventoryToolItem(
                            toolId = obj["toolId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                            id = obj["id"]?.jsonPrimitive?.contentOrNull,
                            remainingActiveSeconds = obj["remainingActiveSeconds"]?.jsonPrimitive?.intOrNull,
                        ))
                        "Decor" -> decors.add(InventoryDecorItem(
                            decorId = obj["decorId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                        ))
                    }
                }
                // Parse storages separately. Seed each capacity from the session's last
                // known value (not the base default) - some InventoryChanged events only
                // carry the storages that actually changed (e.g. DecorShed is rarely
                // touched vs. the constantly-churning Silo/Hutch), so a storage missing
                // from *this* event's list must keep its previously known capacity
                // instead of being reported back down to base.
                val existingSession = _state.value.sessions.find { it.id == sessionId }
                val siloSeeds = mutableListOf<InventorySeedItem>()
                val shedDecors = mutableListOf<InventoryDecorItem>()
                val hutchPets = mutableListOf<InventoryPetItem>()
                val troughCrops = mutableListOf<InventoryCropsItem>()
                val shackTools = mutableListOf<InventoryToolItem>()
                var hutchCapacitySlots = existingSession?.hutchCapacitySlots ?: PriceCalculator.HUTCH_BASE_CAPACITY
                var siloCapacitySlots = existingSession?.siloCapacitySlots ?: PriceCalculator.SILO_BASE_CAPACITY
                var decorShedCapacitySlots = existingSession?.decorShedCapacitySlots ?: PriceCalculator.DECOR_SHED_BASE_CAPACITY
                var toolShackCapacitySlots = existingSession?.toolShackCapacitySlots ?: PriceCalculator.TOOL_SHACK_BASE_CAPACITY
                // Owned storages never go away - only grow this set, don't reset it, so a
                // partial event doesn't make an already-purchased storage look unowned.
                val availableStorages = existingSession?.availableStorages?.toMutableSet() ?: mutableSetOf()

                for (storageEl in event.storages) {
                    val storage = storageEl as? JsonObject ?: continue
                    val storageId = storage["decorId"]?.jsonPrimitive?.contentOrNull ?: continue
                    availableStorages.add(storageId)
                    // Game now sends the capacity directly as "capacitySlots";
                    // fall back to the legacy "capacityLevel" tier if absent.
                    val slots = storage["capacitySlots"]?.jsonPrimitive?.intOrNull
                    val legacyLevel = storage["capacityLevel"]?.jsonPrimitive?.intOrNull ?: 0
                    when (storageId) {
                        "PetHutch" -> hutchCapacitySlots =
                            slots ?: PriceCalculator.calculateHutchCapacity(legacyLevel)
                        "SeedSilo" -> siloCapacitySlots =
                            slots ?: PriceCalculator.calculateSiloCapacity(legacyLevel)
                        "DecorShed" -> decorShedCapacitySlots =
                            slots ?: PriceCalculator.calculateDecorShedCapacity(legacyLevel)
                        "ToolShack" -> toolShackCapacitySlots =
                            slots ?: PriceCalculator.calculateToolShackCapacity(legacyLevel)
                    }
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
                                sourceEggId = obj["sourceEggId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            ))
                            "ToolShack" -> shackTools.add(InventoryToolItem(
                                toolId = obj["toolId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                quantity = obj["quantity"]?.jsonPrimitive?.intOrNull ?: 1,
                            ))
                            "FeedingTrough" -> troughCrops.add(InventoryCropsItem(
                                id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                species = obj["species"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                size = CropSize.clamp(obj["size"]?.jsonPrimitive?.doubleOrNull ?: 0.0),
                                mutations = (obj["mutations"] as? JsonArray)
                                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                                    ?.filter { it.isNotBlank() } ?: emptyList(),
                            ))
                        }
                    }
                }
                // Server confirmed - cancel any pending rollbacks
                this.pets.onInventoryConfirmed()
                garden.onInventoryConfirmed(sessionId)

                // Detect newly hatched pet
                val previousPetIds = garden.takePreHatchPetIds(sessionId)
                val allNewPets = pets + hutchPets
                val hatchedPet = if (previousPetIds != null) {
                    allNewPets.firstOrNull { it.id !in previousPetIds }
                } else null

                AppLog.d(TAG, "[Storage] availableStorages=$availableStorages hutch=$hutchCapacitySlots silo=$siloCapacitySlots decorShed=$decorShedCapacitySlots toolShack=$toolShackCapacitySlots")

                updateSession(sessionId) {
                    it.copy(
                        inventory = InventorySnapshot(seeds, eggs, produce, plants, pets, tools, decors),
                        seedSilo = siloSeeds,
                        decorShed = shedDecors,
                        petHutch = hutchPets,
                        feedingTrough = troughCrops,
                        toolShack = shackTools,
                        favoritedItemIds = event.favoritedItemIds.toSet(),
                        lastHatchedPet = hatchedPet ?: it.lastHatchedPet,
                        magicDust = event.magicDust,
                        hutchCapacitySlots = hutchCapacitySlots,
                        siloCapacitySlots = siloCapacitySlots,
                        decorShedCapacitySlots = decorShedCapacitySlots,
                        toolShackCapacitySlots = toolShackCapacitySlots,
                        availableStorages = availableStorages,
                    )
                }
                this.pets.scheduleTroughAlertCheck(sessionId)
                storage.runAutoStock(sessionId, seeds, decors, tools, siloSeeds, shedDecors, shackTools, availableStorages)
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
                // Server confirmed egg change - cancel pending hatch/grow rollbacks
                garden.onEggsConfirmed(sessionId)
                val freeTiles = clients[sessionId]?.let { garden.computeFreePlantTileCount(it) } ?: 0
                updateSession(sessionId) { it.copy(gardenEggs = newEggs, freePlantTiles = freeTiles) }
            }
            is ClientEvent.ShopsChanged -> {
                val previousShops = _state.value.sessions.find { it.id == sessionId }?.shops.orEmpty()
                val purchases = event.shopPurchases
                // Detect shop restock: the deadline only ever moves LATER when the shop rolled a
                // new stock. Right then shopPurchases may be stale (its reset arrives in a separate
                // patch), and the alert/auto-buy dedup has to start a fresh cycle.
                val restockedTypes = event.shops.filter { shop ->
                    val prevShop = previousShops.find { it.type == shop.type } ?: return@filter false
                    shop.deadlineMs > prevShop.deadlineMs + RESTOCK_COUNTDOWN_JITTER_SEC * 1000L
                }.map { it.type }.toSet()
                val newShops = event.shops.map { shop ->
                    val initialStocks = shop.getItemStocks()
                    val purchaseMap = if (shop.type in restockedTypes) null
                        else shop.purchasesThisRestock(purchases?.get(shop.type) as? JsonObject)
                    val remainingStocks = initialStocks.mapValues { (name, initial) ->
                        val bought = purchaseMap?.get(name)?.jsonPrimitive?.intOrNull ?: 0
                        maxOf(0, initial - bought)
                    }
                    ShopSnapshot(
                        type = shop.type,
                        itemNames = shop.getItemNames(),
                        itemStocks = remainingStocks,
                        initialStocks = initialStocks,
                        restockId = shop.restockId,
                        deadlineMs = shop.deadlineMs,
                    )
                }
                // Server confirmed - cancel any pending rollback jobs for this session
                shops.cancelPendingPurchases(sessionId)
                updateSession(sessionId) { it.copy(shops = newShops) }
                // Only check alerts when actual items changed, not just the restock timer. A
                // restock counts as a change even when it rolled the same items: the stock behind
                // them is new, so it has to alert again.
                val oldItems = previousShops.associate { it.type to it.itemNames }
                val newItems = newShops.associate { it.type to it.itemNames }
                if (oldItems != newItems || restockedTypes.isNotEmpty()) {
                    alertNotifier.checkShopItems(newShops, _state.value.alerts, restockedTypes)
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

    // ---- Bug reports ----

    /**
     * What a bug report may carry about the active session: the player's stable id and
     * name, the connection state and the in-memory WebSocket and service logs. Never
     * the Session itself, which holds the mc_jwt token: the tokens only go in as values
     * to mask, should one ever surface inside a log line. Call on the main thread.
     */
    fun bugReportSnapshot(): BugReportSnapshot {
        val current = _state.value
        val session = current.activeSession
        val client = clients[session.id]
        val selfId = client?.playerId.orEmpty()
        val me = if (client != null && selfId.isNotBlank()) client.gameState.getPlayer(selfId) else null
        val now = System.currentTimeMillis()
        val connectedForSec = if (session.status == SessionStatus.CONNECTED && session.connectedAt > 0) {
            (now - session.connectedAt) / 1000
        } else {
            null
        }
        val serviceEntries = current.serviceLogs
            .take(BugReportContext.MAX_SERVICE_ENTRIES)
            .asReversed()
            .map { BugReportContext.serviceLogEntry(it.timestamp, it.event, it.detail) }
        val tokens = current.sessions
            .map { it.cookie.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { listOf(it, it.removePrefix("mc_jwt=")) }
            .distinct()
        return BugReportSnapshot(
            // The Discord id /collect-state files the account under, not the room's p_ id.
            playerId = me?.databaseUserId?.takeIf { it.isNotBlank() },
            playerName = me?.name?.takeIf { it.isNotBlank() } ?: session.playerName.takeIf { it.isNotBlank() },
            gameVersion = session.gameVersion.takeIf { it.isNotBlank() },
            connection = BugReportConnection(
                status = session.status.name,
                roomId = session.roomId.ifBlank { session.room },
                playersInRoom = session.players,
                gameHost = session.gameUrl,
                connectedForSec = connectedForSec,
                lastError = session.error,
                accountsTotal = current.sessions.size,
                accountsConnected = current.sessions.count { it.status == SessionStatus.CONNECTED },
            ),
            logs = BugReportContext.wsLogEntries(session.wsLogs) + serviceEntries,
            mask = tokens,
        )
    }

    // ---- Backend state reporting (online flag + data sync) ----

    /** Evaluate every connected session for a collect-state send. */
    private suspend fun collectAllSessionStates() {
        val now = System.currentTimeMillis()
        val version = com.mgafk.app.BuildConfig.VERSION_NAME
        val connectedIds = _state.value.sessions
            .filter { it.status == SessionStatus.CONNECTED }
            .map { it.id }
        for (id in connectedIds) {
            val client = clients[id] ?: continue
            withContext(Dispatchers.IO) {
                try {
                    stateCollector.tick(id, client, version, now)
                } catch (e: Exception) {
                    AppLog.w(TAG, "state collect tick failed for $id: ${e.message}")
                }
            }
        }
    }

    /**
     * Fire an immediate collect-state for one session (e.g. right after it
     * connects). The userSlot isn't hydrated yet at connect time, so retry
     * briefly until [StateCollector.tick] actually sends - that's when the slot
     * (coins/garden/inventory) has loaded. The minute tick is the backstop.
     */
    private fun collectStateNow(sessionId: String) {
        clients[sessionId] ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repeat(ON_CONNECT_COLLECT_ATTEMPTS) {
                val client = clients[sessionId] ?: return@launch
                val sent = try {
                    stateCollector.tick(
                        sessionId,
                        client,
                        com.mgafk.app.BuildConfig.VERSION_NAME,
                        System.currentTimeMillis(),
                    )
                } catch (e: Exception) {
                    AppLog.w(TAG, "state collect (on connect) failed for $sessionId: ${e.message}")
                    false
                }
                if (sent) return@launch
                delay(ON_CONNECT_COLLECT_RETRY_MS)
            }
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
