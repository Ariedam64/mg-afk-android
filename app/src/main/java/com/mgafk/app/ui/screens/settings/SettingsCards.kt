package com.mgafk.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mgafk.app.data.model.AppSettings
import com.mgafk.app.data.model.PurchaseMode
import com.mgafk.app.data.model.WakeLockMode
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
) {
    BackgroundCard(settings = settings, onUpdate = onUpdate)
    ShopsSettingsCard(settings = settings, onUpdate = onUpdate)
    StoragesCard(settings = settings, availableStorages = availableStorages, onUpdate = onUpdate)
    ReconnectionCard(settings = settings, onUpdate = onUpdate)
    DeveloperCard(settings = settings, onUpdate = onUpdate)
}

// ── Background & Battery ──

private data class DelayOption(val label: String, val value: Int)

private val SMART_DELAY_OPTIONS = listOf(
    DelayOption("5min", 5),
    DelayOption("15min", 15),
    DelayOption("30min", 30),
)

@Composable
private fun BackgroundCard(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-read battery optimization status when the user returns from system settings
    var batteryOptimized by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(PowerManager::class.java)
                !pm.isIgnoringBatteryOptimizations(context.packageName)
            } else false
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(PowerManager::class.java)
                batteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppCard(title = "Background & Battery", collapsible = true, persistKey = "settings_battery") {

        // ── Wi-Fi Lock ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceBorder.copy(alpha = 0.2f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text("Wi-Fi Lock", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                InfoButton(
                    title = "Wi-Fi Lock",
                    message = "Prevents the Wi-Fi radio from turning off when the screen is locked.\n\n" +
                        "Without this, Android disables Wi-Fi after a few minutes to save battery, " +
                        "which kills the WebSocket connection.\n\n" +
                        "Battery impact: Low. Only keeps the Wi-Fi chip active, not the CPU.",
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = settings.wifiLockEnabled,
                onCheckedChange = { onUpdate(settings.copy(wifiLockEnabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = Accent),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── CPU Wake Lock mode ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CPU Wake Lock", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(modifier = Modifier.width(4.dp))
            InfoButton(
                title = "CPU Wake Lock",
                message = "Prevents the CPU from entering deep sleep mode.\n\n" +
                    "When the CPU sleeps, timers, network callbacks, and reconnection " +
                    "attempts may be delayed or skipped entirely.\n\n" +
                    "Battery impact: Moderate to high. The CPU stays partially active " +
                    "at all times, which drains more battery. " +
                    "Use Smart mode to only enable it when the phone has been locked for a while.",
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WakeLockMode.entries.forEach { mode ->
                val selected = mode == settings.wakeLockMode
                val label = when (mode) {
                    WakeLockMode.OFF -> "Off"
                    WakeLockMode.SMART -> "Smart"
                    WakeLockMode.ALWAYS -> "Always"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) Accent.copy(alpha = 0.12f)
                            else SurfaceBorder.copy(alpha = 0.2f)
                        )
                        .then(
                            if (selected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        )
                        .clickable { onUpdate(settings.copy(wakeLockMode = mode)) }
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

        // Contextual hint based on selected mode
        Spacer(modifier = Modifier.height(6.dp))
        val hint = when (settings.wakeLockMode) {
            WakeLockMode.OFF -> "CPU can sleep freely. Best for battery."
            WakeLockMode.SMART -> "CPU lock activates automatically after the phone is locked."
            WakeLockMode.ALWAYS -> "CPU stays awake at all times. Uses more battery."
        }
        Text(hint, fontSize = 10.sp, color = TextMuted)

        // Smart delay picker
        if (settings.wakeLockMode == WakeLockMode.SMART) {
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Activate after", fontSize = 12.sp, color = TextPrimary)
                SMART_DELAY_OPTIONS.forEach { option ->
                    val selected = option.value == settings.wakeLockAutoDelayMin
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) Accent.copy(alpha = 0.12f)
                                else SurfaceBorder.copy(alpha = 0.2f)
                            )
                            .then(
                                if (selected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            )
                            .clickable { onUpdate(settings.copy(wakeLockAutoDelayMin = option.value)) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = option.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Accent else TextSecondary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── Battery Optimization ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Battery Optimization", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(modifier = Modifier.width(4.dp))
            InfoButton(
                title = "Battery Optimization",
                message = "Android can restrict background apps to save battery.\n\n" +
                    "When MG AFK is \"Optimized\", the system may pause or kill it while " +
                    "the screen is off, interrupting your AFK session.\n\n" +
                    "\"Unrestricted\" lets the app run freely in the background — recommended " +
                    "for uninterrupted AFK sessions.\n\n" +
                    "Tap a button to open the system battery settings.",
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Optimized button → opens full app battery details so user can switch back
            val optimizedSelected = batteryOptimized
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (optimizedSelected) Accent.copy(alpha = 0.12f)
                        else SurfaceBorder.copy(alpha = 0.2f)
                    )
                    .then(
                        if (optimizedSelected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    )
                    .clickable {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        )
                        context.startActivity(intent)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Optimized",
                    fontSize = 13.sp,
                    fontWeight = if (optimizedSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (optimizedSelected) Accent else TextSecondary,
                )
            }

            // Unrestricted button → opens direct ignore battery optimizations request
            val unrestrictedSelected = !batteryOptimized
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (unrestrictedSelected) Accent.copy(alpha = 0.12f)
                        else SurfaceBorder.copy(alpha = 0.2f)
                    )
                    .then(
                        if (unrestrictedSelected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    )
                    .clickable {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            )
                            context.startActivity(intent)
                        }
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Unrestricted",
                    fontSize = 13.sp,
                    fontWeight = if (unrestrictedSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unrestrictedSelected) Accent else TextSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        val batteryHint = if (batteryOptimized) {
            "System may pause the app while screen is off."
        } else {
            "App runs freely in the background."
        }
        Text(batteryHint, fontSize = 10.sp, color = TextMuted)
    }
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) Accent.copy(alpha = 0.12f)
                            else SurfaceBorder.copy(alpha = 0.2f)
                        )
                        .then(
                            if (selected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
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

// ── Storages ──

@Composable
private fun StoragesCard(
    settings: AppSettings,
    availableStorages: Set<String>,
    onUpdate: (AppSettings) -> Unit,
) {
    val hasSilo = "SeedSilo" in availableStorages
    val hasShed = "DecorShed" in availableStorages

    AppCard(title = "Storages", collapsible = true, persistKey = "settings_storages") {
        if (!hasSilo && !hasShed) {
            Text(
                "Place a Seed Silo or Decor Shed in your garden to enable auto-stock features.",
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
    }
}

// ── Reconnection ──

private data class ReconnectDelayOption(val label: String, val ms: Long)

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

// ── Shared components ──

@Composable
private fun InfoButton(title: String, message: String) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .clickable { showDialog = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Info",
            tint = TextMuted,
            modifier = Modifier.size(16.dp),
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            text = { Text(message, fontSize = 13.sp, lineHeight = 18.sp) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("OK", color = Accent)
                }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceBorder.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
            Text(
                description,
                fontSize = 10.sp,
                color = TextMuted,
                lineHeight = 14.sp,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    description: String,
    options: List<ReconnectDelayOption>,
    selectedMs: Long,
    onSelect: (Long) -> Unit,
) {
    Column {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        Text(description, fontSize = 10.sp, color = TextMuted, lineHeight = 14.sp)

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val selected = option.ms == selectedMs
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) Accent.copy(alpha = 0.12f)
                            else SurfaceBorder.copy(alpha = 0.2f)
                        )
                        .then(
                            if (selected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        )
                        .clickable { onSelect(option.ms) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = option.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Accent else TextSecondary,
                    )
                }
            }
        }
    }
}
