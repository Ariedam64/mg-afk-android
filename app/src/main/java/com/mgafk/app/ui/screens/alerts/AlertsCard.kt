package com.mgafk.app.ui.screens.alerts

import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.AlertMode
import com.mgafk.app.data.model.AlertSection
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.theme.rarityBorder

internal val WEATHER_ITEMS = listOf(
    "Sunny" to "Clear Skies",
    "Rain" to "Rain",
    "Frost" to "Snow",
    "AmberMoon" to "Amber Moon",
    "Dawn" to "Dawn",
    "Thunderstorm" to "Thunderstorm",
)

internal const val HUNGER_KEY = "hunger<5"
internal const val TROUGH_KEY = "trough_low"

internal val HUNGER_THRESHOLDS = listOf(5, 10, 15, 20, 25)

/** Emits 4 separate collapsible cards with per-section mode. */
@Composable
fun AlertsCards(
    alerts: AlertConfig,
    apiReady: Boolean,
    onToggle: (key: String, enabled: Boolean) -> Unit,
    onSectionModeChange: (AlertSection, AlertMode) -> Unit,
    onItemModeChange: (key: String, mode: AlertMode) -> Unit,
    onCollapseChange: (key: String, collapsed: Boolean) -> Unit,
    onPetThresholdChange: (Int) -> Unit,
) {
    if (!apiReady) {
        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Accent,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Loading game data…", fontSize = 13.sp, color = TextMuted)
            }
        }
    } else {
        ShopAlertsCard(
            alerts = alerts,
            onToggle = onToggle,
            onItemModeChange = onItemModeChange,
            expanded = alerts.isExpanded("shop_alerts"),
            onCollapseChange = onCollapseChange,
            currentMode = alerts.modeFor(AlertSection.SHOP),
            onModeChange = { onSectionModeChange(AlertSection.SHOP, it) },
        )
        WeatherAlertsCard(
            alerts = alerts,
            onToggle = onToggle,
            onItemModeChange = onItemModeChange,
            expanded = alerts.isExpanded("weather_alerts"),
            onCollapseChange = onCollapseChange,
            currentMode = alerts.modeFor(AlertSection.WEATHER),
            onModeChange = { onSectionModeChange(AlertSection.WEATHER, it) },
        )
        PetAlertsCard(
            alerts = alerts,
            onToggle = onToggle,
            onItemModeChange = onItemModeChange,
            expanded = alerts.isExpanded("pet_alerts"),
            onCollapseChange = onCollapseChange,
            currentMode = alerts.modeFor(AlertSection.PET),
            onThresholdChange = onPetThresholdChange,
            onModeChange = { onSectionModeChange(AlertSection.PET, it) },
        )
        FeedingTroughAlertsCard(
            alerts = alerts,
            onToggle = onToggle,
            onItemModeChange = onItemModeChange,
            expanded = alerts.isExpanded("trough_alerts"),
            onCollapseChange = onCollapseChange,
            currentMode = alerts.modeFor(AlertSection.FEEDING_TROUGH),
            onModeChange = { onSectionModeChange(AlertSection.FEEDING_TROUGH, it) },
        )
        AbilityAlertsCard(
            alerts = alerts,
            onToggle = onToggle,
            expanded = alerts.isExpanded("ability_alerts", defaultExpanded = false),
            onCollapseChange = onCollapseChange,
        )
    }
}

private fun AlertConfig.isExpanded(key: String, defaultExpanded: Boolean = true): Boolean =
    if (key in collapsed) collapsed[key] != true else defaultExpanded

// ── Compact mode picker (reused inside each card) ──

@Composable
internal fun SectionModePicker(currentMode: AlertMode, onModeChange: (AlertMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Mode:", fontSize = 11.sp, color = TextMuted)
        AlertMode.entries.forEach { mode ->
            val selected = mode == currentMode
            val label = when (mode) {
                AlertMode.NOTIFICATION -> "Notification"
                AlertMode.ALARM -> "Alarm"
                AlertMode.CUSTOM -> "Custom"
            }
            val color = when (mode) {
                AlertMode.ALARM -> StatusError
                AlertMode.CUSTOM -> StatusConnecting
                AlertMode.NOTIFICATION -> Accent
            }
            Box(
                modifier = Modifier
                    .clip(AppTheme.shapes.smallShape)
                    .background(if (selected) color.copy(alpha = 0.12f) else SurfaceBorder.copy(alpha = 0.2f))
                    .then(
                        if (selected) Modifier.border(1.dp, color.copy(alpha = 0.5f), AppTheme.shapes.smallShape)
                        else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), AppTheme.shapes.smallShape)
                    )
                    .clickable { onModeChange(mode) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) color else TextMuted,
                )
            }
        }
    }
}

@Composable
internal fun CustomModeHint() {
    Text(
        "Long press an item to switch between notification and alarm.",
        fontSize = 10.sp,
        color = StatusConnecting,
        fontWeight = FontWeight.Medium,
    )
}

/** Small inline mode toggle for switch-based items (Weather / Pet / Trough). */
@Composable
internal fun ItemModeToggle(currentMode: AlertMode, onChange: (AlertMode) -> Unit) {
    val isAlarm = currentMode == AlertMode.ALARM
    val color = if (isAlarm) StatusError else Accent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable {
                onChange(if (isAlarm) AlertMode.NOTIFICATION else AlertMode.ALARM)
            }
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Icon(
            imageVector = if (isAlarm) Icons.Default.NotificationsActive else Icons.Default.Notifications,
            contentDescription = if (isAlarm) "Alarm" else "Notification",
            tint = color,
            modifier = Modifier.size(14.dp),
        )
    }
}

// ── Alert item tile (tappable grid item) ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlertItemTile(
    spriteUrl: String?,
    name: String,
    rarity: String?,
    enabled: Boolean,
    showModeIndicator: Boolean,
    itemMode: AlertMode,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val bgColor = if (enabled) Accent.copy(alpha = 0.1f) else SurfaceDark

    Box(modifier = Modifier.size(76.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(AppTheme.shapes.smallShape)
                .then(
                    if (enabled) Modifier.border(1.5.dp, Accent, AppTheme.shapes.smallShape)
                    else Modifier.rarityBorder(rarity = rarity, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
                )
                .background(bgColor)
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SpriteImage(url = spriteUrl, size = 32.dp, contentDescription = name)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = name,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 11.sp,
            )
        }

        if (enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Active",
                    tint = SurfaceDark,
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        if (showModeIndicator) {
            val isAlarm = itemMode == AlertMode.ALARM
            val color = if (isAlarm) StatusError else Accent
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isAlarm) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                    contentDescription = if (isAlarm) "Alarm mode" else "Notification mode",
                    tint = SurfaceDark,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}
