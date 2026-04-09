package com.mgafk.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mgafk_prefs")

class SessionRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private val KEY_SESSIONS = stringPreferencesKey("mgafk.sessions")
        private val KEY_ACTIVE = stringPreferencesKey("mgafk.activeSession")
        private val KEY_ALERTS = stringPreferencesKey("mgafk.alerts")
        private val KEY_SHOP_TIP = booleanPreferencesKey("mgafk.shopTipDismissed")
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
        val serializable = sessions.map { it.copy(connected = false, busy = false) }
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

    suspend fun isShopTipDismissed(): Boolean {
        return context.dataStore.data.map { it[KEY_SHOP_TIP] ?: false }.first()
    }

    suspend fun dismissShopTip() {
        context.dataStore.edit { prefs ->
            prefs[KEY_SHOP_TIP] = true
        }
    }
}
