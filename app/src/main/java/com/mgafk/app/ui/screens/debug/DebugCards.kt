package com.mgafk.app.ui.screens.debug

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.CrashLog
import com.mgafk.app.data.model.AlertMode
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.model.WsLog
import com.mgafk.app.service.AfkService
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugCards(
    session: Session,
    serviceLogs: List<AfkService.ServiceLog>,
    onTestAlert: (AlertMode) -> Unit,
    onClearWsLogs: () -> Unit,
    onClearServiceLogs: () -> Unit,
) {
    CrashLogDebugCard()
    AlertDebugCard(onTestAlert = onTestAlert)
    WebSocketDebugCard(wsLogs = session.wsLogs, onClear = onClearWsLogs)
    ServiceDebugCard(logs = serviceLogs, onClear = onClearServiceLogs)
}

// ── Crash Log Debug Card ──

@Composable
private fun CrashLogDebugCard() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var content by remember { mutableStateOf(CrashLog.read(context)) }

    AppCard(
        title = "Crash log",
        collapsible = true,
        persistKey = "debug_crash",
        trailing = {
            if (content.isNotBlank()) {
                Text(
                    text = "present",
                    fontSize = 11.sp,
                    color = StatusError,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        },
    ) {
        Text(
            "Uncaught crashes from all processes (main + watchdog) are saved here, " +
                "so night-time background crashes are not lost. Copy and send this if " +
                "the app keeps stopping.",
            fontSize = 11.sp,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DebugActionChip("Refresh", Accent) { content = CrashLog.read(context) }
            if (content.isNotBlank()) {
                DebugActionChip("Copy", Accent) { clipboard.setText(AnnotatedString(content)) }
                DebugActionChip("Clear", StatusError) {
                    CrashLog.clear(context)
                    content = ""
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (content.isBlank()) {
            Text("No crashes recorded yet.", fontSize = 11.sp, color = TextMuted)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceBorder.copy(alpha = 0.15f))
                    .padding(8.dp),
            ) {
                Text(
                    text = content,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun DebugActionChip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

// ── Alert Debug Card ──

@Composable
private fun AlertDebugCard(onTestAlert: (AlertMode) -> Unit) {
    AppCard(title = "Alert", collapsible = true, persistKey = "debug_alert") {
        Text(
            "Simulate alerts without waiting for game events.",
            fontSize = 11.sp,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Accent.copy(alpha = 0.10f))
                    .border(1.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .clickable { onTestAlert(AlertMode.NOTIFICATION) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Test Notification",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Accent,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF87171).copy(alpha = 0.10f))
                    .border(1.dp, Color(0xFFF87171).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .clickable { onTestAlert(AlertMode.ALARM) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Test Alarm",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF87171),
                )
            }
        }
    }
}

// ── WebSocket Debug Card ──

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun levelColor(level: String): Color = when (level) {
    "error" -> Color(0xFFF87171)
    "warn" -> Color(0xFFFBBF24)
    else -> Accent
}

private fun levelDotColor(level: String): Color = when (level) {
    "error" -> Color(0xFFF87171)
    "warn" -> Color(0xFFFBBF24)
    "info" -> Color(0xFF34D399)
    else -> TextMuted
}

@Composable
private fun WebSocketDebugCard(wsLogs: List<WsLog>, onClear: () -> Unit) {
    AppCard(
        title = "WebSocket",
        collapsible = true,
        persistKey = "debug_ws",
        trailing = {
            if (wsLogs.isNotEmpty()) {
                Text(
                    text = "${wsLogs.size} entries",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        },
    ) {
        if (wsLogs.isEmpty()) {
            Text(
                "No WebSocket events yet. Connect a session to see logs.",
                fontSize = 11.sp,
                color = TextMuted,
            )
        } else {
            // Clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceBorder.copy(alpha = 0.3f))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Clear", fontSize = 11.sp, color = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log entries (most recent first)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                wsLogs.forEach { log ->
                    WsLogEntry(log)
                }
            }
        }
    }
}

@Composable
private fun WsLogEntry(log: WsLog) {
    val formattedTime = remember(log.timestamp) {
        timeFormat.format(Date(log.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceBorder.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Level dot
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(levelDotColor(log.level)),
        )

        Spacer(modifier = Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = log.event,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = levelColor(log.level),
                )
                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                )
            }

            if (log.detail.isNotBlank()) {
                Text(
                    text = log.detail,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

// ── Service / Wake Lock Debug Card ──

@Composable
private fun ServiceDebugCard(logs: List<AfkService.ServiceLog>, onClear: () -> Unit) {
    AppCard(
        title = "Service & Wake Lock",
        collapsible = true,
        persistKey = "debug_service",
        trailing = {
            if (logs.isNotEmpty()) {
                Text(
                    text = "${logs.size} entries",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        },
    ) {
        if (logs.isEmpty()) {
            Text(
                "No service events yet. Connect a session to start the service.",
                fontSize = 11.sp,
                color = TextMuted,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceBorder.copy(alpha = 0.3f))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("Clear", fontSize = 11.sp, color = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                logs.forEach { log ->
                    ServiceLogEntry(log)
                }
            }
        }
    }
}

@Composable
private fun ServiceLogEntry(log: AfkService.ServiceLog) {
    val formattedTime = remember(log.timestamp) {
        timeFormat.format(Date(log.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceBorder.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFF818CF8)),
        )

        Spacer(modifier = Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = log.event,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF818CF8),
                )
                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                )
            }

            if (log.detail.isNotBlank()) {
                Text(
                    text = log.detail,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}
