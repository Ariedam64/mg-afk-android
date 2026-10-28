package com.mgafk.app.ui.screens.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.AlertMode
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.data.repository.CoreShops

// ── Shop Alerts ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShopAlertsCard(
    alerts: AlertConfig,
    onToggle: (String, Boolean) -> Unit,
    onItemModeChange: (String, AlertMode) -> Unit,
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

        if (currentMode == AlertMode.CUSTOM) {
            CustomModeHint()
        } else {
            Text(
                "Tap items to get notified when they appear in shop.",
                fontSize = 11.sp,
                color = TextMuted,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val allCategories = remember(MgApi.isReady) {
            val allEntries = MgApi.getPlants().values + MgApi.getEggs().values +
                MgApi.getItems().values + MgApi.getDecors().values
            val dynamic = allEntries
                .flatMap { it.eligibleShops }
                .distinct()
                .map { it.lowercase() }
                .filter { it !in CoreShops.KEYS }
                .sorted()
                .map { key -> "${key.replaceFirstChar { c -> c.uppercase() }} Shop" to key }
            CoreShops.LABELED + dynamic
        }

        allCategories.forEachIndexed { index, (label, category) ->
            val items = remember(category, MgApi.isReady) {
                when (category) {
                    "seed" -> MgApi.getPlants().values.filter {
                        it.eligibleShops.isEmpty() || "Seed" in it.eligibleShops
                    }
                    "tool" -> MgApi.getItems().values.toList()
                    "egg" -> MgApi.getEggs().values.filter {
                        it.eligibleShops.isEmpty() || "Egg" in it.eligibleShops
                    }
                    "decor" -> MgApi.getDecors().values.toList()
                    else -> {
                        val shopName = category.replaceFirstChar { it.uppercase() }
                        (MgApi.getPlants().values + MgApi.getEggs().values +
                            MgApi.getItems().values + MgApi.getDecors().values)
                            .filter { shopName in it.eligibleShops }
                    }
                }
            }
            val activeCount = items.count { entry ->
                alerts.items["shop:$category:${entry.id}"]?.enabled == true
            }

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
                        val item = alerts.items[key]
                        val enabled = item?.enabled == true
                        val itemMode = item?.mode ?: AlertMode.NOTIFICATION
                        AlertItemTile(
                            spriteUrl = entry.sprite,
                            name = entry.name,
                            rarity = entry.rarity,
                            enabled = enabled,
                            showModeIndicator = currentMode == AlertMode.CUSTOM && enabled,
                            itemMode = itemMode,
                            onClick = { onToggle(key, !enabled) },
                            onLongPress = {
                                if (currentMode == AlertMode.CUSTOM) {
                                    val next = if (itemMode == AlertMode.ALARM) AlertMode.NOTIFICATION else AlertMode.ALARM
                                    onItemModeChange(key, next)
                                }
                            },
                        )
                    }
                }
            }

            if (index < allCategories.lastIndex) {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}
