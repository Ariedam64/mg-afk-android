package com.mgafk.app.data.model

import kotlinx.serialization.Serializable

/** Connection state of a single guest bot. */
@Serializable
enum class BotStatus { CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED }

/**
 * Anonymous user style sent on the guest WS URL — same shape the game's
 * browser client sends when an unauthenticated user joins a room.
 */
@Serializable
data class BotAvatar(
    val color: String,
    val avatarTop: String = "Top_DefaultGray.png",
    val avatarMid: String = "Mid_DefaultGray.png",
    val avatarBottom: String = "Bottom_DefaultGray.png",
    val avatarExpression: String = "Expression_Default.png",
)

/** Snapshot of a populate-bot for UI rendering. */
@Serializable
data class BotSnapshot(
    val id: String,
    val playerId: String,
    val name: String,
    val avatar: BotAvatar,
    val status: BotStatus,
    val statusMessage: String = "",
)
