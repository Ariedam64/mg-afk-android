package com.mgafk.app.ui.screens.settings

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary

// ── Shared components ──

@Composable
internal fun InfoButton(title: String, message: String) {
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
internal fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.smallShape)
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
internal fun SettingRow(
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
                        .clip(AppTheme.shapes.smallShape)
                        .background(
                            if (selected) Accent.copy(alpha = 0.12f)
                            else SurfaceBorder.copy(alpha = 0.2f)
                        )
                        .then(
                            if (selected) Modifier.border(1.dp, Accent.copy(alpha = 0.5f), AppTheme.shapes.smallShape)
                            else Modifier.border(1.dp, SurfaceBorder.copy(alpha = 0.4f), AppTheme.shapes.smallShape)
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
