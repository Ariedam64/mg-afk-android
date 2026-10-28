package com.mgafk.app.ui.screens.settings

import androidx.compose.runtime.Composable
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState

/** App settings. */
@Composable
fun SettingsScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    val session = state.activeSession
    SettingsCards(
        settings = state.settings,
        availableStorages = session.availableStorages,
        onUpdate = { newSettings -> viewModel.updateSettings { newSettings } },
        onPreviewAlarm = { viewModel.previewAlarmSound() },
        onStopPreviewAlarm = { viewModel.stopPreviewAlarmSound() },
    )
}
