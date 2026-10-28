package com.mgafk.app.ui.actions

import android.app.Application
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.repository.SessionRepository
import com.mgafk.app.data.websocket.GameActions
import com.mgafk.app.data.websocket.RoomClient
import com.mgafk.app.service.AlertNotifier
import com.mgafk.app.ui.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Logcat tag of the action classes. They all used to live in MainViewModel and still log
 * under its name, so an existing logcat filter keeps catching them.
 */
internal const val LOG_TAG = "MainViewModel"

/**
 * What the action classes share with [com.mgafk.app.ui.MainViewModel]: its scope, its state,
 * the live room clients and the few helpers that write a session back.
 *
 * Owned by the ViewModel, which hands the same instance to every action class.
 */
class SessionContext(
    val app: Application,
    /** The ViewModel's scope: whatever the actions launch dies with it. */
    val scope: CoroutineScope,
    val state: MutableStateFlow<UiState>,
    /** One client per connected session, keyed by session id. */
    val clients: MutableMap<String, RoomClient>,
    val repo: SessionRepository,
    val alertNotifier: AlertNotifier,
) {
    fun session(id: String): Session? = state.value.sessions.find { it.id == id }

    /** The game commands of a session's client, or null when it has none. */
    fun actions(sessionId: String): GameActions? = clients[sessionId]?.actions

    fun updateSession(id: String, transform: (Session) -> Session) {
        updateSessionInMemory(id, transform)
        persist()
    }

    /**
     * Same, minus the write to disk.
     *
     * For state the server hands back on every reconnect and that changes on its own schedule:
     * persisting it buys nothing and would turn a ticking countdown into a disk write per tick.
     */
    fun updateSessionInMemory(id: String, transform: (Session) -> Session) {
        state.update { s ->
            s.copy(sessions = s.sessions.map { if (it.id == id) transform(it) else it })
        }
    }

    fun persist() {
        scope.launch {
            repo.saveSessions(state.value.sessions)
        }
    }
}

/** Cancels and drops the pending rollbacks of one session, keyed "sessionId:...". */
internal fun MutableMap<String, Job>.cancelForSession(sessionId: String) {
    keys.filter { it.startsWith("$sessionId:") }.forEach { key ->
        remove(key)?.cancel()
    }
}
