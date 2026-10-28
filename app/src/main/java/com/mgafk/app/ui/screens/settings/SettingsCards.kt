package com.mgafk.app.ui.screens.settings

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AppSettings
import com.mgafk.app.data.model.PlantPlacementMode
import com.mgafk.app.data.model.PurchaseMode
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary

@Composable
fun SettingsCards(
    settings: AppSettings,
    availableStorages: Set<String> = emptySet(),
    onUpdate: (AppSettings) -> Unit,
    onPreviewAlarm: () -> Unit = {},
    onStopPreviewAlarm: () -> Unit = {},
) {
    BackgroundCard(settings = settings, onUpdate = onUpdate)
    ShopsSettingsCard(settings = settings, onUpdate = onUpdate)
    GardenSettingsCard(settings = settings, onUpdate = onUpdate)
    StoragesCard(settings = settings, availableStorages = availableStorages, onUpdate = onUpdate)
    GameplayCard(settings = settings, onUpdate = onUpdate)
    AlarmCard(
        settings = settings,
        onUpdate = onUpdate,
        onPreviewAlarm = onPreviewAlarm,
        onStopPreviewAlarm = onStopPreviewAlarm,
    )
    ReconnectionCard(settings = settings, onUpdate = onUpdate)
    DeveloperCard(settings = settings, onUpdate = onUpdate)
}

// ── Shops ──

@Composable
private fun ShopsSettingsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    AppCard(title = "Shops", collapsible = true, persistKey = "settings_shops") {
        Text("Purchase mode", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PurchaseMode.entries.forEach { mode ->
                val selected = mode == settings.purchaseMode
                val label = when (mode) {
                    PurchaseMode.SINGLE -> "Single"
                    PurchaseMode.BULK -> "Bulk"
                    PurchaseMode.HYBRID -> "Hybrid"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(AppTheme.shapes.smallShape)
                        .background(
                            if (selected) Accent.copy(alpha = 0.12f)
                            else SurfaceBorder.copy(alpha = 0.2f)
                        )
                        .then(
                            if (selected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), AppTheme.shapes.smallShape)
                            else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), AppTheme.shapes.smallShape)
                        )
                        .clickable { onUpdate(settings.copy(purchaseMode = mode)) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Accent else TextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val hint = when (settings.purchaseMode) {
            PurchaseMode.SINGLE -> "Tap buys x1 only."
            PurchaseMode.BULK -> "Tap buys all remaining stock at once."
            PurchaseMode.HYBRID -> "Tap buys x1, hold buys all remaining stock."
        }
        Text(hint, fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
private fun GardenSettingsCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    AppCard(title = "Garden", collapsible = true, persistKey = "settings_garden") {
        Text("Manual planting", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlantPlacementMode.entries.forEach { mode ->
                val selected = mode == settings.plantPlacementMode
                val label = when (mode) {
                    PlantPlacementMode.FREE_TILE -> "Free tiles"
                    PlantPlacementMode.GRID -> "Grid"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(AppTheme.shapes.smallShape)
                        .background(
                            if (selected) Accent.copy(alpha = 0.12f)
                            else SurfaceBorder.copy(alpha = 0.2f)
                        )
                        .then(
                            if (selected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), AppTheme.shapes.smallShape)
                            else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), AppTheme.shapes.smallShape)
                        )
                        .clickable { onUpdate(settings.copy(plantPlacementMode = mode)) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Accent else TextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val hint = when (settings.plantPlacementMode) {
            PlantPlacementMode.FREE_TILE -> "Planting drops the seed on the first empty tile."
            PlantPlacementMode.GRID -> "Planting opens the garden grid so you pick the tile."
        }
        Text(hint, fontSize = 10.sp, color = TextMuted)
    }
}

// ── Storages ──

@Composable
private fun StoragesCard(
    settings: AppSettings,
    availableStorages: Set<String>,
    onUpdate: (AppSettings) -> Unit,
) {
    val hasSilo = "SeedSilo" in availableStorages
    val hasShed = "DecorShed" in availableStorages
    val hasShack = "ToolShack" in availableStorages

    AppCard(title = "Storages", collapsible = true, persistKey = "settings_storages") {
        if (!hasSilo && !hasShed && !hasShack) {
            Text(
                "Place a Seed Silo, Decor Shed or Tool Shack in your garden to enable auto-stock features.",
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 15.sp,
            )
            return@AppCard
        }

        if (hasSilo) {
            ToggleRow(
                title = "Auto-stock Seed Silo",
                description = "Whenever a seed in your inventory matches a species already in the silo, move it in automatically.",
                checked = settings.autoStockSeedSilo,
                onCheckedChange = { onUpdate(settings.copy(autoStockSeedSilo = it)) },
            )
        }

        if (hasSilo && hasShed) Spacer(modifier = Modifier.height(10.dp))

        if (hasShed) {
            ToggleRow(
                title = "Auto-stock Decor Shed",
                description = "Whenever a decor in your inventory matches a decor already in the shed, move it in automatically.",
                checked = settings.autoStockDecorShed,
                onCheckedChange = { onUpdate(settings.copy(autoStockDecorShed = it)) },
            )
        }

        if ((hasSilo || hasShed) && hasShack) Spacer(modifier = Modifier.height(10.dp))

        if (hasShack) {
            ToggleRow(
                title = "Auto-stock Tool Shack",
                description = "Whenever a tool in your inventory matches a tool already in the shack, move it in automatically.",
                checked = settings.autoStockToolShack,
                onCheckedChange = { onUpdate(settings.copy(autoStockToolShack = it)) },
            )
        }
    }
}

// ── Gameplay ──

@Composable
private fun GameplayCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    AppCard(title = "Gameplay", collapsible = true, persistKey = "settings_gameplay") {
        ToggleRow(
            title = "Instant hatch",
            description = "Skip the egg-opening animation and show the hatched pet result instantly.",
            checked = settings.instantHatch,
            onCheckedChange = { onUpdate(settings.copy(instantHatch = it)) },
        )
    }
}

// ── Reconnection ──

internal data class ReconnectDelayOption(val label: String, val ms: Long)

private val KICKED_DELAY_OPTIONS = listOf(
    ReconnectDelayOption("10s", 10000),
    ReconnectDelayOption("30s", 30000),
    ReconnectDelayOption("1min", 60000),
    ReconnectDelayOption("2min", 120000),
)

@Composable
private fun ReconnectionCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    AppCard(title = "Reconnection", collapsible = true, persistKey = "settings_reconnect") {
        Text(
            "The app automatically retries when the connection is lost.",
            fontSize = 11.sp,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(12.dp))

        ToggleRow(
            title = "Notify on disconnect",
            description = "Send a notification when a session loses connection.",
            checked = settings.notifyOnDisconnect,
            onCheckedChange = { onUpdate(settings.copy(notifyOnDisconnect = it)) },
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingRow(
            label = "Kicked by another session",
            description = "Wait time before reconnecting when the same account connects from another device.",
            options = KICKED_DELAY_OPTIONS,
            selectedMs = settings.retrySupersededDelayMs,
            onSelect = { onUpdate(settings.copy(retrySupersededDelayMs = it)) },
        )
    }
}

// ── Developer Options ──

@Composable
private fun DeveloperCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    AppCard(title = "Developer Options", collapsible = true, persistKey = "settings_dev") {
        ToggleRow(
            title = "Show Debug menu",
            description = "Adds a Debug section in the navigation with WebSocket logs and test tools.",
            checked = settings.showDebugMenu,
            onCheckedChange = { onUpdate(settings.copy(showDebugMenu = it)) },
        )
    }
}
