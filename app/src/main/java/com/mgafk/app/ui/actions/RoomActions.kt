package com.mgafk.app.ui.actions

import com.mgafk.app.data.model.BotSnapshot
import com.mgafk.app.data.model.BotStatus
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.data.repository.AriesApi
import com.mgafk.app.data.repository.VersionFetcher
import com.mgafk.app.data.websocket.BotClient
import com.mgafk.app.data.websocket.BotEvent
import com.mgafk.app.data.websocket.BotNameGenerator
import com.mgafk.app.data.websocket.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The room a session sits in: chat, the guest bots that fill it, and the public room list.
 *
 * [connect] and [disconnect] are the ViewModel's own, which [joinPublicRoom] goes through to
 * move a session to another room.
 */
class RoomActions(
    private val ctx: SessionContext,
    private val connect: (String) -> Unit,
    private val disconnect: (String) -> Unit,
) {
    // Populate bots, scoped per session. Outer map keyed by sessionId, inner by botId.
    private val botClients = mutableMapOf<String, MutableMap<String, BotClient>>()

    fun sendChat(sessionId: String, message: String) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return
        ctx.actions(sessionId)?.chat(trimmed)
    }

    // ──── Populate (guest bots for sell bonus) ─────────────────────────────

    /** Max players a Magic Garden room can hold (game-side constraint). */
    private val ROOM_MAX_PLAYERS = 6

    /**
     * Spawn enough guest bots to fill empty slots in this session's room.
     * Host-only. Caller is expected to gate the action in the UI; we still
     * guard here.
     */
    fun populateRoom(sessionId: String) {
        val session = ctx.session(sessionId) ?: return
        if (!session.connected) return
        if (session.playerId.isBlank() || session.playerId != session.hostPlayerId) return

        val activeBots = session.bots.count { it.status != BotStatus.DISCONNECTED }
        val freeSlots = ROOM_MAX_PLAYERS - session.players - activeBots
        if (freeSlots <= 0) return

        val host = session.gameUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .ifBlank { "magicgarden.gg" }
        val version = session.gameVersion
        if (version.isBlank()) return

        val takenNames = session.bots.map { it.name }.toMutableSet()
        val sessionBots = botClients.getOrPut(sessionId) { mutableMapOf() }

        repeat(freeSlots) {
            val (name, avatar) = BotNameGenerator.next(takenNames)
            takenNames.add(name)
            val botId = UUID.randomUUID().toString()
            val playerId = IdGenerator.generatePlayerId()
            val bot = BotClient(botId = botId, playerId = playerId, name = name, avatar = avatar)
            sessionBots[botId] = bot

            val snapshot = BotSnapshot(
                id = botId,
                playerId = playerId,
                name = name,
                avatar = avatar,
                status = BotStatus.CONNECTING,
            )
            ctx.updateSession(sessionId) { it.copy(bots = it.bots + snapshot) }

            // Subscribe to bot events to update its snapshot in session state.
            ctx.scope.launch {
                bot.events.collect { event ->
                    if (event is BotEvent.StatusChanged) {
                        ctx.updateSession(sessionId) { s ->
                            s.copy(bots = s.bots.map {
                                if (it.id == botId) it.copy(
                                    status = event.status,
                                    statusMessage = event.message,
                                ) else it
                            })
                        }
                    }
                }
            }

            bot.connect(
                version = version,
                room = session.room,
                host = host,
                versionFetcher = { VersionFetcher.fetchVersionForRoom(host = host, room = session.room) },
            )
        }
    }

    /** Disconnect a single bot and remove it from the session. */
    fun disconnectBot(sessionId: String, botId: String) {
        val sessionBots = botClients[sessionId] ?: return
        sessionBots.remove(botId)?.disconnect()
        ctx.updateSession(sessionId) { it.copy(bots = it.bots.filter { b -> b.id != botId }) }
    }

    /** Disconnect every bot tied to this session. */
    fun disconnectAllBots(sessionId: String) {
        botClients.remove(sessionId)?.values?.forEach { it.disconnect() }
        ctx.updateSession(sessionId) { it.copy(bots = emptyList()) }
    }

    // ---- Public Rooms (Aries API) ----

    fun fetchPublicRooms() {
        ctx.state.update { it.copy(publicRoomsLoading = true) }
        ctx.scope.launch(Dispatchers.IO) {
            val rooms = AriesApi.fetchRooms()
            ctx.state.update { it.copy(publicRooms = rooms, publicRoomsLoading = false) }
        }
    }

    /** Join a public room: disconnect if connected, change room code, reconnect. */
    fun joinPublicRoom(sessionId: String, roomId: String) {
        val session = ctx.session(sessionId) ?: return
        val wasConnected = session.status == SessionStatus.CONNECTED || session.status == SessionStatus.CONNECTING
        if (wasConnected) disconnect(sessionId)
        ctx.updateSession(sessionId) { it.copy(room = roomId) }
        ctx.scope.launch { ctx.repo.saveSessions(ctx.state.value.sessions) }
        ctx.scope.launch {
            if (wasConnected) delay(500)
            connect(sessionId)
        }
    }
}
