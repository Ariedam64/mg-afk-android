package com.mgafk.app.ui.screens.alerts

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary

// ── Ability Alerts ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AbilityAlertsCard(
    alerts: AlertConfig,
    onToggle: (String, Boolean) -> Unit,
    expanded: Boolean,
    onCollapseChange: (String, Boolean) -> Unit,
) {
    val abilities = remember(MgApi.isReady) {
        MgApi.getAbilities().values.sortedBy { it.name.lowercase() }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(abilities, query) {
        if (query.isBlank()) {
            abilities
        } else {
            val q = query.trim().lowercase()
            abilities.filter { it.name.lowercase().contains(q) || it.id.lowercase().contains(q) }
        }
    }
    val totalActive = alerts.items.count { (key, item) -> key.startsWith("ability:") && item.enabled }

    AppCard(
        title = "Ability Alerts",
        collapsible = true,
        expanded = expanded,
        onExpandedChange = { onCollapseChange("ability_alerts", !it) },
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
        Text(
            "Get notified when one of your pets procs an ability.",
            fontSize = 11.sp,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (abilities.isEmpty()) {
            Text(
                if (MgApi.isReady) "No abilities." else "Loading...",
                fontSize = 11.sp,
                color = TextMuted,
            )
            return@AppCard
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search ability...", fontSize = 12.sp) },
            singleLine = true,
            textStyle = TextStyle(fontSize = 12.sp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppTheme.shapes.smallShape),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Accent,
            ),
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Text("No matching abilities.", fontSize = 11.sp, color = TextMuted)
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                filtered.forEach { entry ->
                    val key = "ability:${entry.id}"
                    val enabled = alerts.items[key]?.enabled == true
                    AbilityAlertChip(
                        name = entry.name,
                        dotColor = abilityDotColor(entry.color),
                        enabled = enabled,
                        onClick = { onToggle(key, !enabled) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AbilityAlertChip(
    name: String,
    dotColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = AppTheme.shapes.smallShape
    Row(
        modifier = Modifier
            .clip(shape)
            .then(if (enabled) Modifier.border(1.5.dp, Accent, shape) else Modifier)
            .background(if (enabled) Accent.copy(alpha = 0.12f) else SurfaceDark)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) TextPrimary else TextSecondary,
        )
        if (enabled) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Active",
                tint = Accent,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** Solid color for an ability's badge dot, parsed from its API color string. */
@Composable
@ReadOnlyComposable
private fun abilityDotColor(raw: String?): Color {
    if (raw == null) return TextMuted
    val hex = Regex("#[0-9A-Fa-f]{6}").find(raw)?.value ?: return TextMuted
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        TextMuted
    }
}
