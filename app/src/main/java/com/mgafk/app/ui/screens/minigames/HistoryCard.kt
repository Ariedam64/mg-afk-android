package com.mgafk.app.ui.screens.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.repository.Transaction
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
private val timeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

@Composable
fun HistoryCard(
    transactions: List<Transaction>,
    loading: Boolean,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoad() }

    AppCard(
        modifier = modifier,
        title = "History",
        collapsible = true,
        initiallyExpanded = false,
        persistKey = "minigames.history",
    ) {
        if (loading && transactions.isEmpty()) {
            Text("Loading...", fontSize = 12.sp, color = TextMuted)
        } else if (transactions.isEmpty()) {
            Text("No transactions yet.", fontSize = 12.sp, color = TextMuted)
        } else {
            Column(
                modifier = Modifier.heightIn(max = 300.dp),
            ) {
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    transactions.forEachIndexed { index, tx ->
                        TransactionRow(tx)
                        if (index < transactions.lastIndex) {
                            HorizontalDivider(
                                color = SurfaceBorder,
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: Transaction) {
    val isBonus = tx.ref_id == "first_deposit_bonus"
    val config = if (isBonus) TxConfig("Welcome Bonus", Icons.Outlined.EmojiEvents, StatusConnecting) else txConfig(tx.type)
    val isCredit = tx.from_player == null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(config.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(config.icon, contentDescription = null, tint = config.color, modifier = Modifier.size(16.dp))
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Label + time
        Column(modifier = Modifier.weight(1f)) {
            Text(config.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isBonus) StatusConnecting else TextPrimary)
            Text(
                text = formatTime(tx.created_at),
                fontSize = 10.sp,
                color = TextMuted,
            )
        }

        // Amount
        Text(
            text = "${if (isCredit) "+" else "-"}${numberFormat.format(tx.amount)}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = if (isCredit) StatusConnected else StatusError,
        )
    }
}

private data class TxConfig(val label: String, val icon: ImageVector, val color: Color)

private fun txConfig(type: String): TxConfig = when (type) {
    "deposit" -> TxConfig("Deposit", Icons.Outlined.ArrowDownward, StatusConnected)
    "withdraw" -> TxConfig("Withdraw", Icons.Outlined.ArrowUpward, Accent)
    "bet" -> TxConfig("Bet", Icons.Outlined.Casino, StatusError)
    "win" -> TxConfig("Win", Icons.Outlined.EmojiEvents, StatusConnected)
    "transfer" -> TxConfig("Transfer", Icons.Outlined.SwapHoriz, Accent)
    else -> TxConfig(type.replaceFirstChar { it.uppercase() }, Icons.Outlined.SwapHoriz, TextMuted)
}

private fun formatTime(iso: String): String {
    return try {
        val instant = Instant.parse(iso)
        instant.atZone(ZoneId.systemDefault()).format(timeFormatter)
    } catch (_: Exception) {
        iso
    }
}
