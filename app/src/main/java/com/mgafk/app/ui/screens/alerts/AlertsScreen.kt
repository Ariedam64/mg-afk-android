package com.mgafk.app.ui.screens.alerts

import androidx.compose.runtime.Composable
import com.mgafk.app.data.model.AlertItem
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState

/** What raises a notification or an alarm. */
@Composable
fun AlertsScreen(
    state: UiState,
    viewModel: MainViewModel,
) {
    val session = state.activeSession
    AlertsCards(
        alerts = state.alerts,
        apiReady = state.apiReady,
        onToggle = { key, enabled ->
            viewModel.updateAlerts { config ->
                val items = config.items.toMutableMap()
                // Preserve any existing mode - only flip the enabled flag.
                val current = items[key] ?: AlertItem()
                items[key] = current.copy(enabled = enabled)
                config.copy(items = items)
            }
        },
        onSectionModeChange = { section, mode ->
            viewModel.updateAlerts { config ->
                config.copy(sectionModes = config.sectionModes + (section.key to mode))
            }
        },
        onItemModeChange = { key, mode ->
            viewModel.updateAlerts { config ->
                val items = config.items.toMutableMap()
                val current = items[key] ?: AlertItem()
                items[key] = current.copy(mode = mode)
                config.copy(items = items)
            }
        },
        onCollapseChange = { key, collapsed ->
            viewModel.updateAlerts { config ->
                config.copy(collapsed = config.collapsed + (key to collapsed))
            }
        },
        onPetThresholdChange = { threshold ->
            viewModel.updateAlerts { config ->
                config.copy(petHungerThreshold = threshold)
            }
        },
    )
}
