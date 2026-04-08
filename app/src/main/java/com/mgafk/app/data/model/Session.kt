package com.mgafk.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Session(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Session 1",
    val autoName: Boolean = true,
    val cookie: String = "",
    val room: String = "",
    val gameUrl: String = "magicgarden.gg",
    val reconnect: ReconnectConfig = ReconnectConfig(),
    val connected: Boolean = false,
    val busy: Boolean = false,
    val status: SessionStatus = SessionStatus.IDLE,
    val error: String = "",
    val reconnectCountdown: String = "",
    val players: Int = 0,
    val uptime: String = "00:00:00",
    val playerId: String = "",
    val playerName: String = "",
    val roomId: String = "",
    val weather: String = "",
    val pets: List<PetSnapshot> = emptyList(),
    val logs: List<AbilityLog> = emptyList(),
    val shops: List<ShopSnapshot> = emptyList(),
)

@Serializable
enum class SessionStatus { IDLE, CONNECTING, CONNECTED, ERROR }

@Serializable
data class ReconnectConfig(
    val unknown: Boolean = true,
    val delays: ReconnectDelays = ReconnectDelays(),
    val codes: Map<Int, Boolean> = mapOf(
        4100 to true, 4200 to true, 4250 to true, 4300 to true,
        4310 to true, 4400 to true, 4500 to true, 4700 to true,
        4710 to true, 4800 to true,
    ),
)

@Serializable
data class ReconnectDelays(
    val supersededMs: Long = 30000,
    val otherMs: Long = 1500,
)

/** Serializable snapshot of a pet for Session persistence */
@Serializable
data class PetSnapshot(
    val id: String = "",
    val name: String = "",
    val species: String = "",
    val hunger: Double = 0.0,
    val index: Int = 0,
    val mutations: List<String> = emptyList(),
)

@Serializable
data class AbilityLog(
    val timestamp: Long = 0,
    val action: String = "",
    val petName: String = "",
    val petSpecies: String = "",
    val petMutations: List<String> = emptyList(),
    val slotIndex: Int = 0,
)

/** Serializable snapshot of a shop for Session persistence */
@Serializable
data class ShopSnapshot(
    val type: String = "",
    val itemNames: List<String> = emptyList(),
    val itemStocks: Map<String, Int> = emptyMap(),
    val secondsUntilRestock: Int = 0,
)
