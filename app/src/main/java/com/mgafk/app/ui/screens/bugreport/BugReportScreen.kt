package com.mgafk.app.ui.screens.bugreport

import androidx.compose.runtime.Composable
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState

/** Bug report form. */
@Composable
fun BugReportScreen(
    state: UiState,
    viewModel: MainViewModel,
    isVisible: Boolean,
) {
    val session = state.activeSession
    BugReportCards(
        visible = isVisible,
        sessionConnected = session.status == SessionStatus.CONNECTED,
        // Read at tap time only: the session data and logs of that moment.
        snapshot = { viewModel.bugReportSnapshot() },
    )
}
