package com.mgafk.app.ui.screens.alerts

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.AlertMode
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary

// ── Pet Alerts ──

@Composable
internal fun PetAlertsCard(
    alerts: AlertConfig,
    onToggle: (String, Boolean) -> Unit,
    onItemModeChange: (String, AlertMode) -> Unit,
    expanded: Boolean,
    onCollapseChange: (String, Boolean) -> Unit,
    currentMode: AlertMode,
    onThresholdChange: (Int) -> Unit,
    onModeChange: (AlertMode) -> Unit,
) {
    val item = alerts.items[HUNGER_KEY]
    val enabled = item?.enabled == true
    val itemMode = item?.mode ?: AlertMode.NOTIFICATION
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

        if (currentMode == AlertMode.CUSTOM) {
            Spacer(modifier = Modifier.height(6.dp))
            CustomModeHint()
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppTheme.shapes.smallShape)
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
            if (currentMode == AlertMode.CUSTOM && enabled) {
                ItemModeToggle(currentMode = itemMode) { onItemModeChange(HUNGER_KEY, it) }
                Spacer(modifier = Modifier.width(8.dp))
            }
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
internal fun FeedingTroughAlertsCard(
    alerts: AlertConfig,
    onToggle: (String, Boolean) -> Unit,
    onItemModeChange: (String, AlertMode) -> Unit,
    expanded: Boolean,
    onCollapseChange: (String, Boolean) -> Unit,
    currentMode: AlertMode,
    onModeChange: (AlertMode) -> Unit,
) {
    val item = alerts.items[TROUGH_KEY]
    val enabled = item?.enabled == true
    val itemMode = item?.mode ?: AlertMode.NOTIFICATION

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

        if (currentMode == AlertMode.CUSTOM) {
            Spacer(modifier = Modifier.height(6.dp))
            CustomModeHint()
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppTheme.shapes.smallShape)
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
            if (currentMode == AlertMode.CUSTOM && enabled) {
                ItemModeToggle(currentMode = itemMode) { onItemModeChange(TROUGH_KEY, it) }
                Spacer(modifier = Modifier.width(8.dp))
            }
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
                    .clip(AppTheme.shapes.smallShape)
                    .background(if (isSelected) Accent.copy(alpha = 0.12f) else SurfaceBorder.copy(alpha = 0.2f))
                    .then(
                        if (isSelected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), AppTheme.shapes.smallShape)
                        else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), AppTheme.shapes.smallShape)
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
