package com.mgafk.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.AppSettings
import com.mgafk.app.data.model.migrated
import com.mgafk.app.data.model.PetTeam
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.model.Tip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import com.mgafk.app.data.AppJson

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mgafk_prefs")

class SessionRepository(private val context: Context) {
    private val json = AppJson.storage

    companion object {
        private val KEY_SESSIONS = stringPreferencesKey("mgafk.sessions")
        private val KEY_ACTIVE = stringPreferencesKey("mgafk.activeSession")
        private val KEY_ALERTS = stringPreferencesKey("mgafk.alerts")
        private val KEY_COLLAPSED_CARDS = stringPreferencesKey("mgafk.collapsedCards")
        private val KEY_SETTINGS = stringPreferencesKey("mgafk.settings")
        private val KEY_NOTIFIED_VERSION = stringPreferencesKey("mgafk.lastNotifiedVersion")
    }

    suspend fun loadSessions(): List<Session> {
        val raw = context.dataStore.data.map { it[KEY_SESSIONS] }.first()
        if (raw.isNullOrBlank()) return listOf(Session())
        return try {
            json.decodeFromString<List<Session>>(raw)
        } catch (_: Exception) {
            listOf(Session())
        }
    }

    suspend fun saveSessions(sessions: List<Session>) {
        val serializable = sessions.map {
            it.copy(
                connected = false,
                busy = false,
                status = com.mgafk.app.data.model.SessionStatus.IDLE,
                connectedAt = 0,
                wsLogs = emptyList(),
            )
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_SESSIONS] = json.encodeToString(serializable)
        }
    }

    suspend fun loadActiveSessionId(): String? {
        return context.dataStore.data.map { it[KEY_ACTIVE] }.first()
    }

    suspend fun saveActiveSessionId(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE] = id
        }
    }

    suspend fun loadAlerts(): AlertConfig {
        val raw = context.dataStore.data.map { it[KEY_ALERTS] }.first()
        if (raw.isNullOrBlank()) return AlertConfig()
        return try {
            json.decodeFromString<AlertConfig>(raw)
        } catch (_: Exception) {
            AlertConfig()
        }
    }

    suspend fun saveAlerts(config: AlertConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ALERTS] = json.encodeToString(config)
        }
    }

    suspend fun loadCollapsedCards(): Map<String, Boolean> {
        val raw = context.dataStore.data.map { it[KEY_COLLAPSED_CARDS] }.first()
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, Boolean>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun saveCollapsedCards(collapsed: Map<String, Boolean>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COLLAPSED_CARDS] = json.encodeToString(collapsed)
        }
    }

    suspend fun loadSettings(): AppSettings {
        val raw = context.dataStore.data.map { it[KEY_SETTINGS] }.first()
        if (raw.isNullOrBlank()) return AppSettings()
        return try {
            json.decodeFromString<AppSettings>(raw).migrated()
        } catch (_: Exception) {
            AppSettings()
        }
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SETTINGS] = json.encodeToString(settings)
        }
    }

    /** The tips the player has dismissed, read in one pass. */
    suspend fun loadDismissedTips(): Set<Tip> {
        val prefs = context.dataStore.data.first()
        return Tip.entries.filter { prefs[booleanPreferencesKey(it.key)] ?: false }.toSet()
    }

    suspend fun dismissTip(tip: Tip) {
        context.dataStore.edit { prefs ->
            prefs[booleanPreferencesKey(tip.key)] = true
        }
    }

    suspend fun getLastNotifiedVersion(): String? {
        return context.dataStore.data.map { it[KEY_NOTIFIED_VERSION] }.first()
    }

    suspend fun setLastNotifiedVersion(version: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFIED_VERSION] = version
        }
    }
}
