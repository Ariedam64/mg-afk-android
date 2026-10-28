package com.mgafk.app.ui.screens.alerts

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.AlertMode
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary

// ── Weather Alerts ──

@Composable
internal fun WeatherAlertsCard(
    alerts: AlertConfig,
    onToggle: (String, Boolean) -> Unit,
    onItemModeChange: (String, AlertMode) -> Unit,
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

        if (currentMode == AlertMode.CUSTOM) {
            CustomModeHint()
        } else {
            Text(
                "Get notified on weather changes.",
                fontSize = 11.sp,
                color = TextMuted,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WEATHER_ITEMS.forEach { (apiKey, displayName) ->
                val key = "weather:$displayName"
                val item = alerts.items[key]
                val enabled = item?.enabled == true
                val itemMode = item?.mode ?: AlertMode.NOTIFICATION
                val spriteUrl = weatherSprites[apiKey]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppTheme.shapes.smallShape)
                        .background(SurfaceBorder.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (spriteUrl != null) {
                        SpriteImage(url = spriteUrl, size = 20.dp, contentDescription = displayName)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(displayName, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    if (currentMode == AlertMode.CUSTOM && enabled) {
                        ItemModeToggle(currentMode = itemMode) { onItemModeChange(key, it) }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
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
