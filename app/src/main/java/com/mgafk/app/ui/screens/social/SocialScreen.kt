package com.mgafk.app.ui.screens.social

import androidx.compose.runtime.Composable
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState

/** Public rooms to browse and join. */
@Composable
fun SocialScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    val session = state.activeSession
    com.mgafk.app.ui.screens.social.PublicRoomsCard(
        rooms = state.publicRooms,
        loading = state.publicRoomsLoading,
        currentRoomId = session.roomId.ifBlank { session.room },
        isConnected = session.status == SessionStatus.CONNECTED,
        onRefresh = { viewModel.room.fetchPublicRooms() },
        onJoin = { roomId -> viewModel.room.joinPublicRoom(session.id, roomId) },
    )
}
