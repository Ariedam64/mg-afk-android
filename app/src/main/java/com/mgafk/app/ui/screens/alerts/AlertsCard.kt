package com.mgafk.app.ui.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.AlertMode
import com.mgafk.app.data.model.AlertSection
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.theme.rarityBorder

// Game-authentic rarity colors
private val RarityCommon = Color(0xFFE7E7E7)
private val RarityUncommon = Color(0xFF67BD4D)
private val RarityRare = Color(0xFF0071C6)
private val RarityLegendary = Color(0xFFFFC734)
private val RarityMythical = Color(0xFF9944A7)
private val RarityDivine = Color(0xFFFF7835)
private val RarityCelestial = Color(0xFFFF00FF)

private fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "common" -> RarityCommon
    "uncommon" -> RarityUncommon
    "rare" -> RarityRare
    "legendary" -> RarityLegendary
    "mythical", "mythic" -> RarityMythical
    "divine" -> RarityDivine
    "celestial" -> RarityCelestial
    else -> TextMuted
}

private val WEATHER_ITEMS = listOf(
    "Sunny" to "Clear Skies",
    "Rain" to "Rain",
    "Frost" to "Snow",
    "AmberMoon" to "Amber Moon",
    "Dawn" to "Dawn",
    "Thunderstorm" to "Thunderstorm",
)

private const val HUNGER_KEY = "hunger<5"
private const val TROUGH_KEY = "trough_low"

private val HUNGER_THRESHOLDS = listOf(5, 10, 15, 20, 25)

private val SHOP_CATEGORIES = listOf(
    "Seeds" to "seed",
    "Tools" to "tool",
    "Eggs" to "egg",
    "Decors" to "decor",
)

/** Emits 4 separate collapsible cards with per-section mode. */
@Composable
fun AlertsCards(
    alerts: AlertConfig,
    apiReady: Boolean,
    onToggle: (key: String, enabled: Boolean) -> Unit,
    onSectionModeChange: (AlertSection, AlertMode) -> Unit,
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
        ShopAlertsCard(alerts, onToggle, alerts.isExpanded("shop_alerts"), onCollapseChange, alerts.modeFor(AlertSection.SHOP)) { onSectionModeChange(AlertSection.SHOP, it) }
        WeatherAlertsCard(alerts, onToggle, alerts.isExpanded("weather_alerts"), onCollapseChange, alerts.modeFor(AlertSection.WEATHER)) { onSectionModeChange(AlertSection.WEATHER, it) }
        PetAlertsCard(alerts, onToggle, alerts.isExpanded("pet_alerts"), onCollapseChange, alerts.modeFor(AlertSection.PET), onPetThresholdChange) { onSectionModeChange(AlertSection.PET, it) }
        FeedingTroughAlertsCard(alerts, onToggle, alerts.isExpanded("trough_alerts"), onCollapseChange, alerts.modeFor(AlertSection.FEEDING_TROUGH)) { onSectionModeChange(AlertSection.FEEDING_TROUGH, it) }
    }
}

private fun AlertConfig.isExpanded(key: String, defaultExpanded: Boolean = true): Boolean =
    if (key in collapsed) collapsed[key] != true else defaultExpanded

// ── Compact mode picker (reused inside each card) ──

@Composable
private fun SectionModePicker(currentMode: AlertMode, onModeChange: (AlertMode) -> Unit) {
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
            }
            val color = if (mode == AlertMode.ALARM) Color(0xFFF87171) else Accent
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) color.copy(alpha = 0.12f) else SurfaceBorder.copy(alpha = 0.2f))
                    .then(
                        if (selected) Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
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

// ── Shop Alerts ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShopAlertsCard(
    alerts: AlertConfig,
    onToggle: (String, Boolean) -> Unit,
    expanded: Boolean,
    onCollapseChange: (String, Boolean) -> Unit,
    currentMode: AlertMode,
    onModeChange: (AlertMode) -> Unit,
) {
    val totalActive = alerts.items.count { (key, item) -> key.startsWith("shop:") && item.enabled }

    AppCard(
        title = "Shop Alerts",
        collapsible = true,
        expanded = expanded,
        onExpandedChange = { onCollapseChange("shop_alerts", !it) },
        trailing = {
            if (totalActive > 0) {
                Text(
                    text = "$totalActive active",
                    fontSize = 11.sp,
                    color = Accent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        },
    ) {
        SectionModePicker(currentMode = currentMode, onModeChange = onModeChange)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Tap items to get notified when they appear in shop.",
            fontSize = 11.sp,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(12.dp))

        SHOP_CATEGORIES.forEachIndexed { index, (label, category) ->
            val items = remember(category, MgApi.isReady) {
                when (category) {
                    "seed" -> MgApi.getPlants()
                    "tool" -> MgApi.getItems()
                    "egg" -> MgApi.getEggs()
                    "decor" -> MgApi.getDecors()
                    else -> emptyMap()
                }.values.toList()
            }
            val activeCount = items.count { entry ->
                alerts.items["shop:$category:${entry.id}"]?.enabled == true
            }

            // Category header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                if (activeCount > 0) {
                    Text("$activeCount", fontSize = 11.sp, color = Accent)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (items.isEmpty() && !MgApi.isReady) {
                Text("Loading...", fontSize = 11.sp, color = TextMuted)
            } else if (items.isEmpty()) {
                Text("No items.", fontSize = 11.sp, color = TextMuted)
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items.forEach { entry ->
                        val key = "shop:$category:${entry.id}"
                        val enabled = alerts.items[key]?.enabled == true
                        AlertItemTile(
                            spriteUrl = entry.sprite,
                            name = entry.name,
                            rarity = entry.rarity,
                            enabled = enabled,
                            onClick = { onToggle(key, !enabled) },
                        )
                    }
                }
            }

            if (index < SHOP_CATEGORIES.lastIndex) {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

// ── Weather Alerts ──

@Composable
private fun WeatherAlertsCard(
    alerts: AlertConfig,
    onToggle: (String, Boolean) -> Unit,
    expanded: Boolean,
    onCollapseChange: (String, Boolean) -> Unit,
    currentMode: AlertMode,
    onModeChange: (AlertMode) -> Unit,
) {
    val weatherSprites = remember(MgApi.isReady) { MgApi.getWeathers().mapValues { it.value.sprite } }
    val activeCount = WEATHER_ITEMS.count { (_, display) ->
        alerts.items["weather:$display"]?.enabled == true
    }

    AppCard(
        title = "Weather Alerts",
        collapsible = true,
        expanded = expanded,
        onExpandedChange = { onCollapseChange("weather_alerts", !it) },
        trailing = {
            if (activeCount > 0) {
                Text(
                    text = "$activeCount active",
                    fontSize = 11.sp,
                    color = Accent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        },
    ) {
        SectionModePicker(currentMode = currentMode, onModeChange = onModeChange)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Get notified on weather changes.",
            fontSize = 11.sp,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WEATHER_ITEMS.forEach { (apiKey, displayName) ->
                val key = "weather:$displayName"
                val enabled = alerts.items[key]?.enabled == true
                val spriteUrl = weatherSprites[apiKey]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceBorder.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (spriteUrl != null) {
                        SpriteImage(url = spriteUrl, size = 20.dp, contentDescription = displayName)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(displayName, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { onToggle(key, it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                        )
                    }
                }
            }
        }
    }
}

// ── Pet Alerts ──

@Composable
private fun PetAlertsCard(
    alerts: AlertConfig,
    onToggle: (String, Boolean) -> Unit,
    expanded: Boolean,
    onCollapseChange: (String, Boolean) -> Unit,
    currentMode: AlertMode,
    onThresholdChange: (Int) -> Unit,
    onModeChange: (AlertMode) -> Unit,
) {
    val enabled = alerts.items[HUNGER_KEY]?.enabled == true
    val threshold = alerts.petHungerThreshold

    AppCard(
        title = "Pet Alerts",
        collapsible = true,
        expanded = expanded,
        onExpandedChange = { onCollapseChange("pet_alerts", !it) },
        trailing = {
            if (enabled) {
                Text(
                    text = "1 active",
                    fontSize = 11.sp,
                    color = Accent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        },
    ) {
        SectionModePicker(currentMode = currentMode, onModeChange = onModeChange)

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceBorder.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Hunger < $threshold%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle(HUNGER_KEY, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        ThresholdPicker(
            label = "Threshold",
            values = HUNGER_THRESHOLDS,
            selected = threshold,
            format = { "$it%" },
            onSelect = onThresholdChange,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            "Get notified when any pet drops below $threshold% hunger.",
            fontSize = 11.sp,
            color = TextMuted,
        )
    }
}

// ── Feeding Trough Alerts ──

@Composable
private fun FeedingTroughAlertsCard(
    alerts: AlertConfig,
    onToggle: (String, Boolean) -> Unit,
    expanded: Boolean,
    onCollapseChange: (String, Boolean) -> Unit,
    currentMode: AlertMode,
    onModeChange: (AlertMode) -> Unit,
) {
    val enabled = alerts.items[TROUGH_KEY]?.enabled == true

    AppCard(
        title = "Feeding Trough Alerts",
        collapsible = true,
        expanded = expanded,
        onExpandedChange = { onCollapseChange("trough_alerts", !it) },
        trailing = {
            if (enabled) {
                Text(
                    text = "1 active",
                    fontSize = 11.sp,
                    color = Accent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        },
    ) {
        SectionModePicker(currentMode = currentMode, onModeChange = onModeChange)

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceBorder.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Trough low (≤ 1 item)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle(TROUGH_KEY, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            "Get notified when the feeding trough has 1 or fewer items.",
            fontSize = 11.sp,
            color = TextMuted,
        )
    }
}

// ── Threshold picker (reusable) ──

@Composable
private fun ThresholdPicker(
    label: String,
    values: List<Int>,
    selected: Int,
    format: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label:", fontSize = 11.sp, color = TextMuted)
        values.forEach { value ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Accent.copy(alpha = 0.12f) else SurfaceBorder.copy(alpha = 0.2f))
                    .then(
                        if (isSelected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    text = format(value),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Accent else TextMuted,
                )
            }
        }
    }
}

// ── Alert item tile (tappable grid item) ──

@Composable
private fun AlertItemTile(
    spriteUrl: String?,
    name: String,
    rarity: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (enabled) Accent.copy(alpha = 0.1f) else SurfaceDark

    Box(modifier = Modifier.size(76.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .then(
                    if (enabled) Modifier.border(1.5.dp, Accent, RoundedCornerShape(10.dp))
                    else Modifier.rarityBorder(rarity = rarity, width = 1.5.dp, shape = RoundedCornerShape(10.dp), alpha = 0.5f)
                )
                .background(bgColor)
                .clickable(onClick = onClick)
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

        // Checkmark badge
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
    }
}
