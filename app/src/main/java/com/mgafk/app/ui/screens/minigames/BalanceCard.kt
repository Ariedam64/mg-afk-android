package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary

@Composable
fun BalanceCard(
    gameBalance: Long?,
    gameBalanceLoading: Boolean,
    gameBalanceError: String?,
    casinoBalance: Long?,
    casinoBalanceLoading: Boolean,
    casinoConnected: Boolean,
    onRefreshGameBalance: () -> Unit,
    onRefreshCasinoBalance: () -> Unit,
    onConnectCasino: () -> Unit,
    onDeposit: () -> Unit = {},
    onWithdraw: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        if (gameBalance == null && !gameBalanceLoading) onRefreshGameBalance()
        onRefreshCasinoBalance()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Left: Balances card ──
        AppCard(modifier = Modifier.weight(1f)) {
            // Game balance
            var showInfoTip by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Game Balance", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextMuted, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "Info",
                            tint = TextMuted.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp).clickable { showInfoTip = !showInfoTip },
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = BREAD_SPRITE_URL, contentDescription = "Bread", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        if (gameBalance != null) {
                            Text(numberFormat.format(gameBalance), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                        } else {
                            Text(if (gameBalanceLoading) "..." else "--", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        }
                    }
                }
                val rotation = if (gameBalanceLoading) {
                    val transition = rememberInfiniteTransition(label = "refresh_spin")
                    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "refresh_angle")
                    angle
                } else 0f
                IconButton(onClick = { if (!gameBalanceLoading) onRefreshGameBalance() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = if (gameBalanceLoading) TextMuted else Accent, modifier = Modifier.size(16.dp).rotate(rotation))
                }
            }

            // Info tip
            if (showInfoTip) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Can't see your balance? Go to Dashboard, logout your Discord token, then login again and connect.",
                    fontSize = 10.sp,
                    color = TextMuted,
                    lineHeight = 14.sp,
                )
            }

            // Token expired error
            if (gameBalanceError == "token_expired") {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(StatusError.copy(alpha = 0.1f)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = StatusError, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Token expired — re-login", fontSize = 10.sp, color = StatusError)
                }
            } else if (gameBalanceError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Error: $gameBalanceError", fontSize = 10.sp, color = StatusError)
            }

            // Divider
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Casino balance
            if (casinoConnected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Casino Balance", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextMuted, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = BREAD_SPRITE_URL, contentDescription = "Bread", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            if (casinoBalance != null) {
                                Text(numberFormat.format(casinoBalance), fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusConnected)
                            } else {
                                Text(if (casinoBalanceLoading) "..." else "--", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                            }
                        }
                    }
                    val casinoRotation = if (casinoBalanceLoading) {
                        val transition = rememberInfiniteTransition(label = "casino_spin")
                        val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(800, easing = LinearEasing)), label = "casino_angle")
                        angle
                    } else 0f
                    IconButton(onClick = { if (!casinoBalanceLoading) onRefreshCasinoBalance() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = if (casinoBalanceLoading) TextMuted else StatusConnected, modifier = Modifier.size(16.dp).rotate(casinoRotation))
                    }
                }
            } else {
                Text("Casino Balance", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextMuted, letterSpacing = 0.5.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Accent.copy(alpha = 0.1f))
                        .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onConnectCasino)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Connect with Discord", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                }
            }
        }

        // ── Right: Deposit / Withdraw buttons ──
        if (casinoConnected) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Deposit (blue)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Accent.copy(alpha = 0.08f))
                        .border(1.dp, Accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onDeposit)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Deposit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                }
                // Withdraw (green)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(StatusConnected.copy(alpha = 0.08f))
                        .border(1.dp, StatusConnected.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onWithdraw)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Withdraw", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = StatusConnected)
                }
            }
        }
    }
}
