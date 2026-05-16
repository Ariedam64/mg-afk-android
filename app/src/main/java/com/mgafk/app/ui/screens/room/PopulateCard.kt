package com.mgafk.app.ui.screens.room

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.BotSnapshot
import com.mgafk.app.data.model.BotStatus
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary

private const val ROOM_MAX = 6

@Composable
fun PopulateCard(
    isHost: Boolean,
    playersConnected: Int,
    bots: List<BotSnapshot>,
    onPopulate: () -> Unit,
    onDisconnectBot: (botId: String) -> Unit,
    onDisconnectAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeBots = bots.count { it.status != BotStatus.DISCONNECTED }
    val freeSlots = (ROOM_MAX - playersConnected - activeBots).coerceAtLeast(0)
    val controlsAlpha = if (isHost) 1f else 0.4f

    AppCard(
        modifier = modifier,
        title = "Populate Room",
        collapsible = true,
        persistKey = "room.populate",
        trailing = {
            if (activeBots > 0) {
                Text(
                    text = "$activeBots bot${if (activeBots > 1) "s" else ""}",
                    fontSize = 11.sp,
                    color = Accent,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        },
    ) {
        Text(
            text = if (isHost) {
                "Fill empty slots with guest bots to unlock the friends-bonus sell multiplier."
            } else {
                "Only the host can populate the room."
            },
            fontSize = 11.sp,
            color = TextMuted,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Populate button
        val canPopulate = isHost && freeSlots > 0
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (canPopulate) Accent.copy(alpha = 0.12f) else SurfaceBorder.copy(alpha = 0.2f))
                .border(
                    1.dp,
                    if (canPopulate) Accent.copy(alpha = 0.5f) else SurfaceBorder.copy(alpha = 0.4f),
                    RoundedCornerShape(8.dp),
                )
                .alpha(controlsAlpha)
                .then(if (canPopulate) Modifier.clickable { onPopulate() } else Modifier)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    !isHost -> "Populate"
                    freeSlots == 0 -> "Room full"
                    else -> "Populate (+$freeSlots)"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (canPopulate) Accent else TextMuted,
            )
        }

        if (bots.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                bots.forEach { bot ->
                    BotRow(bot = bot, onDisconnect = { onDisconnectBot(bot.id) })
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Disconnect all
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF87171).copy(alpha = 0.1f))
                    .border(1.dp, Color(0xFFF87171).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { onDisconnectAll() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Disconnect All",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF87171),
                )
            }
        }
    }
}

@Composable
private fun BotRow(bot: BotSnapshot, onDisconnect: () -> Unit) {
    val statusColor = when (bot.status) {
        BotStatus.CONNECTED -> StatusConnected
        BotStatus.CONNECTING, BotStatus.RECONNECTING -> Accent
        BotStatus.DISCONNECTED -> Color(0xFFF87171)
    }
    val statusLabel = when (bot.status) {
        BotStatus.CONNECTING -> "Connecting…"
        BotStatus.CONNECTED -> "Connected"
        BotStatus.RECONNECTING -> bot.statusMessage.ifBlank { "Reconnecting…" }
        BotStatus.DISCONNECTED -> bot.statusMessage.ifBlank { "Disconnected" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceBorder.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(bot.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(statusLabel, fontSize = 10.sp, color = TextMuted)
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable { onDisconnect() }
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Disconnect ${bot.name}",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
