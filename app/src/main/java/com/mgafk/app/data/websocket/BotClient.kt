package com.mgafk.app.data.websocket

import com.mgafk.app.data.AppLog
import com.mgafk.app.data.model.BotAvatar
import com.mgafk.app.data.model.BotStatus
import com.mgafk.app.data.model.ReconnectConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Stripped-down WebSocket client for an anonymous guest "bot" connection.
 *
 * Only what's needed to keep a guest slot alive in the room:
 * - opens the WS via [UrlBuilder.buildGuestUrl] (no Cookie header)
 * - drops every inbound message (we don't observe game state)
 * - reconnects with backoff on unexpected close, refreshing the game
 *   version each retry (mirrors the recent main-session fix)
 */
class BotClient(
    val botId: String,
    val playerId: String,
    val name: String,
    val avatar: BotAvatar,
) {
    private val TAG = "BotClient[$botId]"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // long-lived
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var manualClose = false
    private var retryJob: Job? = null
    private var retryCount = 0
    private var socketToken = 0
    private var lastOpts: ConnectOpts? = null

    var reconnectConfig: ReconnectConfig = ReconnectConfig()

    private val _events = MutableSharedFlow<BotEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<BotEvent> = _events.asSharedFlow()

    data class ConnectOpts(
        val version: String,
        val room: String,
        val host: String,
        val versionFetcher: (suspend () -> String)? = null,
    )

    fun connect(
        version: String,
        room: String,
        host: String = Constants.DEFAULT_HOST,
        versionFetcher: (suspend () -> String)? = null,
        isRetry: Boolean = false,
    ) {
        webSocket?.let { it.close(1000, null); webSocket = null }
        if (!isRetry) retryCount = 0
        cancelRetryJob()
        manualClose = false

        val effectiveVersion = version.trim()
        // Preserve fetcher across retries (the retry path calls connect() with
        // a null versionFetcher; we keep the original closure that way).
        val preservedFetcher = if (isRetry) lastOpts?.versionFetcher else versionFetcher
        lastOpts = ConnectOpts(effectiveVersion, room, host, preservedFetcher)

        val url = UrlBuilder.buildGuestUrl(host, effectiveVersion, room, playerId, name, avatar)
        AppLog.d(TAG, "connect() url=$url retry=$retryCount")

        val token = ++socketToken
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Constants.DEFAULT_UA)
            .header("Origin", "https://$host")
            .build()

        emit(BotEvent.StatusChanged(BotStatus.CONNECTING))

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (token != socketToken) return
                retryCount = 0
                emit(BotEvent.StatusChanged(BotStatus.CONNECTED))
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                // The server uses an application-level ping/pong on top of the
                // WS protocol pings — RoomClient does the same. If we don't
                // reply, the server closes the connection after ~30s with 4400.
                if (text == "ping" || text == "\"ping\"") {
                    webSocket.send("pong")
                }
                // All other messages are ignored — bots don't observe state.
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
                AppLog.w(TAG, "ws failure: ${t.message}")
                handleClose(1006, t.message ?: t.toString())
            }
        })
    }

    fun disconnect() {
        manualClose = true
        cancelRetryJob()
        socketToken++
        webSocket?.close(1000, "manual")
        webSocket = null
        emit(BotEvent.StatusChanged(BotStatus.DISCONNECTED, "Disconnected"))
    }

    private fun handleClose(code: Int, reason: String) {
        if (manualClose) {
            emit(BotEvent.StatusChanged(BotStatus.DISCONNECTED, "code $code"))
            return
        }
        if (!shouldReconnect(code)) {
            emit(BotEvent.StatusChanged(BotStatus.DISCONNECTED, "code $code · ${reason.take(40)}"))
            return
        }
        scheduleReconnect(code, reason)
    }

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

    private fun scheduleReconnect(code: Int, reason: String) {
        val opts = lastOpts ?: return
        if (retryCount >= Constants.RETRY_MAX) return
        retryCount++
        val delayMs = getReconnectDelay(code)
        emit(BotEvent.StatusChanged(BotStatus.RECONNECTING, "code $code · attempt $retryCount"))
        cancelRetryJob()
        retryJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            val freshVersion = resolveFreshVersion(opts)
            try {
                connect(
                    version = freshVersion,
                    room = opts.room,
                    host = opts.host,
                    isRetry = true,
                )
            } catch (e: Exception) {
                emit(BotEvent.StatusChanged(BotStatus.DISCONNECTED, e.message ?: "reconnect failed"))
            }
        }
    }

    private suspend fun resolveFreshVersion(opts: ConnectOpts): String {
        val fetcher = opts.versionFetcher ?: return opts.version
        return try {
            val fetched = fetcher.invoke().trim()
            if (fetched.isBlank()) opts.version else fetched
        } catch (e: Exception) {
            AppLog.w(TAG, "version fetch failed, using cached: ${e.message}")
            opts.version
        }
    }

    private fun cancelRetryJob() {
        retryJob?.cancel()
        retryJob = null
    }

    private fun emit(event: BotEvent) {
        scope.launch { _events.emit(event) }
    }
}

sealed class BotEvent {
    data class StatusChanged(val status: BotStatus, val message: String = "") : BotEvent()
}
