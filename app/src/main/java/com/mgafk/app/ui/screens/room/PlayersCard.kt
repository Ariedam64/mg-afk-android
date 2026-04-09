package com.mgafk.app.ui.screens.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.PlayerSnapshot
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusIdle
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import kotlin.math.abs

@Composable
fun PlayersCard(
    players: List<PlayerSnapshot>,
    gameVersion: String,
    gameHost: String,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier,
        title = "Players",
        collapsible = true,
        trailing = {
            Text("${players.size}", fontSize = 11.sp, color = TextMuted)
        },
    ) {
        if (players.isEmpty()) {
            Text("No players.", fontSize = 12.sp, color = TextMuted)
        } else {
            players.forEach { player ->
                PlayerRow(player, gameVersion, gameHost)
            }
        }
    }
}

@Composable
private fun PlayerRow(
    player: PlayerSnapshot,
    gameVersion: String,
    gameHost: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceBorder.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        PlayerAvatar(
            player = player,
            gameVersion = gameVersion,
            gameHost = gameHost,
            size = 40.dp,
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Name + status
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = player.name.ifBlank { player.id },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (player.isConnected) StatusConnected else StatusIdle),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (player.isConnected) "Online" else "Offline",
                    fontSize = 10.sp,
                    color = TextMuted,
                )
            }
        }

        // Coins
        Text(
            text = formatCoins(player.coins),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Accent,
        )
    }
}

private fun formatCoins(coins: Double): String {
    val absCoins = abs(coins)
    val sign = if (coins < 0) "-" else ""
    return when {
        absCoins >= 1_000_000_000_000 -> "${sign}${formatNumber(absCoins / 1_000_000_000_000)}T"
        absCoins >= 1_000_000_000 -> "${sign}${formatNumber(absCoins / 1_000_000_000)}B"
        absCoins >= 1_000_000 -> "${sign}${formatNumber(absCoins / 1_000_000)}M"
        absCoins >= 1_000 -> "${sign}${formatNumber(absCoins / 1_000)}K"
        else -> "${sign}${absCoins.toLong()}"
    }
}

private fun formatNumber(value: Double): String {
    return if (value >= 100) {
        value.toLong().toString()
    } else if (value >= 10) {
        String.format("%.1f", value).removeSuffix(".0")
    } else {
        String.format("%.1f", value).removeSuffix(".0")
    }
}
