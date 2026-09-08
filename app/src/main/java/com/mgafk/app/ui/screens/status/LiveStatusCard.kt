package com.mgafk.app.ui.screens.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.websocket.Constants
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

@Composable
fun LiveStatusCard(session: Session, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, title = "Live Status", collapsible = true, persistKey = "dashboard.liveStatus") {
        StatusRow("Players", "${session.players}")
        UptimeRow(session.connectedAt)
        StatusRow("Player", session.playerName.ifBlank { "-" })
        StatusRow("Room ID", session.roomId.ifBlank { "-" })
        StatusRow("Player ID", session.playerId.ifBlank { "-" })
    }
}

@Composable
private fun UptimeRow(connectedAt: Long) {
    var uptime by remember { mutableStateOf("00:00:00") }

    LaunchedEffect(connectedAt) {
        if (connectedAt <= 0) {
            uptime = "00:00:00"
            return@LaunchedEffect
        }
        while (true) {
            uptime = Constants.fmtDuration(System.currentTimeMillis() - connectedAt)
            delay(1_000)
        }
    }

    StatusRow("Uptime", uptime, mono = true)
}

@Composable
private fun StatusRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = if (mono) Accent else TextPrimary,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

