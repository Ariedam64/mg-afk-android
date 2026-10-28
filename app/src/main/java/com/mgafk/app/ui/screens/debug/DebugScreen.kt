package com.mgafk.app.ui.screens.debug

import androidx.compose.runtime.Composable
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState

/** WebSocket and service logs. */
@Composable
fun DebugScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    val session = state.activeSession
    DebugCards(
        session = session,
        serviceLogs = state.serviceLogs,
        onTestAlert = { mode -> viewModel.testAlert(mode) },
        onClearWsLogs = { viewModel.clearWsLogs(session.id) },
        onClearServiceLogs = { viewModel.clearServiceLogs() },
    )
}
