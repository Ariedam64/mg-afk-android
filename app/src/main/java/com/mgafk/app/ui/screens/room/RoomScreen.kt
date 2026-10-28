package com.mgafk.app.ui.screens.room

import androidx.compose.runtime.Composable
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState

/** Master switch for the Populate UI. Flip to true to re-enable. */
private const val POPULATE_ENABLED = false

/** Players and chat of the room the session is in. */
@Composable
fun RoomScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    val session = state.activeSession
    PlayersCard(
        players = session.playersList,
        gameVersion = session.gameVersion,
        gameHost = session.gameUrl,
        hostPlayerId = session.hostPlayerId,
    )
    // Populate hidden for now - code kept so we can flip this back on
    // without touching MainViewModel / BotClient.
    @Suppress("KotlinConstantConditions")
    if (POPULATE_ENABLED) {
        PopulateCard(
            isHost = session.playerId.isNotBlank() && session.playerId == session.hostPlayerId,
            playersConnected = session.players,
            bots = session.bots,
            onPopulate = { viewModel.room.populateRoom(session.id) },
            onDisconnectBot = { botId -> viewModel.room.disconnectBot(session.id, botId) },
            onDisconnectAll = { viewModel.room.disconnectAllBots(session.id) },
        )
    }
    ChatCard(
        messages = session.chatMessages,
        players = session.playersList,
        gameVersion = session.gameVersion,
        gameHost = session.gameUrl,
        onSend = { message -> viewModel.room.sendChat(session.id, message) },
    )
}
