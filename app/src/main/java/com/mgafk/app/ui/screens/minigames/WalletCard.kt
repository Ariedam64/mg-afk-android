package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mgafk.app.ui.DepositUiState
import com.mgafk.app.ui.WithdrawUiState
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.AccentDim
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.temporal.ChronoUnit

private enum class WalletMode { IDLE, DEPOSIT, WITHDRAW }

@Composable
fun WalletCard(
    deposit: DepositUiState,
    depositConfig: com.mgafk.app.data.repository.DepositConfigResponse?,
    depositConfigLoading: Boolean,
    withdraw: WithdrawUiState,
    onRequestDeposit: (Long) -> Unit,
    onCancelDeposit: () -> Unit,
    onRefreshDeposit: () -> Unit,
    onResetDeposit: () -> Unit,
    onRequestWithdraw: (Long) -> Unit,
    onResetWithdraw: () -> Unit,
    gameBalance: Long? = null,
    casinoBalance: Long? = null,
    gameBalanceLoading: Boolean = false,
    onRefreshGameBalance: () -> Unit = {},
    initialMode: String? = null,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(
        when (initialMode) {
            "deposit" -> WalletMode.DEPOSIT
            "withdraw" -> WalletMode.WITHDRAW
            else -> WalletMode.IDLE
        }
    ) }
    var amount by remember { mutableStateOf("") }

    // Auto-refresh game balance whenever the withdraw flow opens, so the max
    // withdrawable amount reflects the player's current in-game coin total
    // (the server refuses `/doughnate` if it pushes the total past 2,000,000).
    LaunchedEffect(mode) {
        if (mode == WalletMode.WITHDRAW) onRefreshGameBalance()
    }
    // Refresh after a deposit is confirmed — the player just transferred game
    // breads to the casino, so their in-game balance dropped.
    LaunchedEffect(deposit.status) {
        if (deposit.status == "confirmed") onRefreshGameBalance()
    }

    // Show deposit popup when pending/confirmed/expired/cancelled
    val showDepositPopup = deposit.active || deposit.status in listOf("confirmed", "expired", "cancelled")
    if (showDepositPopup) {
        DepositPopup(
            deposit = deposit,
            depositConfig = depositConfig,
            onCancel = {
                if (deposit.active) onCancelDeposit()
                else { onResetDeposit(); mode = WalletMode.IDLE }
            },
            onRefresh = { onRefreshDeposit() },
            onDone = {
                onResetDeposit()
                mode = WalletMode.IDLE
            },
        )
    }

    AppCard(modifier = modifier, title = "Wallet") {
        // ── Deposit flow (amount input only) ──
        AnimatedVisibility(
            visible = mode == WalletMode.DEPOSIT && !showDepositPopup,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DepositAmountInput(
                amount = amount,
                onAmountChange = { amount = it },
                loading = deposit.loading || depositConfigLoading,
                error = deposit.error,
                maxDeposit = depositConfig?.limits?.maxDeposit ?: 100_000,
                onConfirm = { amt -> onRequestDeposit(amt) },
                onCancel = { onResetDeposit(); mode = WalletMode.IDLE },
            )
        }

        // ── Withdraw flow ──
        AnimatedVisibility(
            visible = mode == WalletMode.WITHDRAW,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            WithdrawFlow(
                amount = amount,
                onAmountChange = { amount = it },
                withdraw = withdraw,
                gameBalance = gameBalance,
                casinoBalance = casinoBalance,
                gameBalanceLoading = gameBalanceLoading,
                onRefreshGameBalance = onRefreshGameBalance,
                onConfirm = { amt -> onRequestWithdraw(amt) },
                onCancel = { onResetWithdraw(); mode = WalletMode.IDLE },
            )
        }
    }
}

// ── Deposit amount input (inline in card) ──

@Composable
private fun DepositAmountInput(
    amount: String,
    onAmountChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    maxDeposit: Long = 100_000,
    onConfirm: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val parsedAmount = amount.toLongOrNull()
    val isValid = parsedAmount != null && parsedAmount > 0 && parsedAmount <= maxDeposit

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Deposit Breads", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Accent)
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Creating deposit...", fontSize = 13.sp, color = TextMuted)
            }
        } else {
            OutlinedTextField(
                value = amount,
                onValueChange = { new -> onAmountChange(new.filter { it.isDigit() }) },
                label = { Text("Amount") },
                placeholder = { Text("Max ${numberFormat.format(maxDeposit)}") },
                leadingIcon = {
                    AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedLabelColor = Accent,
                    cursorColor = Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(error, fontSize = 11.sp, color = StatusError)
            }

            Spacer(modifier = Modifier.height(12.dp))
            FilledButton(
                label = "Continue",
                color = Accent,
                enabled = isValid,
                onClick = { parsedAmount?.let { onConfirm(it) } },
            )
        }
    }
}

// ── Deposit popup (pending / confirmed / expired) ──

@Composable
private fun DepositPopup(
    deposit: DepositUiState,
    depositConfig: com.mgafk.app.data.repository.DepositConfigResponse?,
    onCancel: () -> Unit,
    onRefresh: () -> Unit,
    onDone: () -> Unit,
) {
    var copied by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { /* non-dismissable while pending */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(20.dp),
        confirmButton = {},
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (deposit.status) {
                    "confirmed" -> {
                        val subtitle = buildString {
                            append("${numberFormat.format(deposit.amount)} breads added to your casino balance.")
                            if (deposit.refundedAmount > 0) {
                                append("\n${numberFormat.format(deposit.refundedAmount)} breads surplus refunded.")
                            }
                        }
                        StatusBanner(
                            icon = Icons.Outlined.CheckCircle,
                            color = StatusConnected,
                            title = "Deposit confirmed!",
                            subtitle = subtitle,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledButton(label = "Done", color = StatusConnected, onClick = onDone)
                    }

                    "cancelled" -> {
                        if (deposit.refundedAmount > 0) {
                            StatusBanner(
                                icon = Icons.Outlined.CheckCircle,
                                color = StatusConnecting,
                                title = "Deposit cancelled",
                                subtitle = "${numberFormat.format(deposit.refundedAmount)} breads refunded to your account.",
                            )
                        } else {
                            StatusBanner(
                                icon = Icons.Outlined.Close,
                                color = TextMuted,
                                title = "Deposit cancelled",
                                subtitle = "No breads were sent.",
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledButton(label = "Close", color = Accent, onClick = onDone)
                    }

                    "expired" -> {
                        val expiryMin = depositConfig?.limits?.depositExpiryMinutes ?: 5
                        val subtitle = buildString {
                            append("The $expiryMin minute window has passed.")
                            if (deposit.refundedAmount > 0) {
                                append(" ${numberFormat.format(deposit.refundedAmount)} breads refunded.")
                            } else {
                                append(" Try again.")
                            }
                        }
                        StatusBanner(
                            icon = Icons.Outlined.ErrorOutline,
                            color = StatusError,
                            title = "Deposit expired",
                            subtitle = subtitle,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledButton(label = "Close", color = Accent, onClick = onDone)
                    }

                    else -> {
                        // ── Pending ──
                        Text(
                            text = "Go to #bakery on the Magic Circle Discord and use the /doughnate command:",
                            fontSize = 12.sp,
                            color = TextMuted,
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Command info with avatar
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceBorder.copy(alpha = 0.3f))
                                .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                        ) {
                            Text("/doughnate", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Accent)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("target", fontSize = 12.sp, color = TextMuted)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val avatarUrl = depositConfig?.account?.avatar
                                    if (avatarUrl != null) {
                                        AsyncImage(
                                            model = avatarUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape),
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(depositConfig?.account?.displayName ?: "...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("amount", fontSize = 12.sp, color = TextMuted)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val remaining = deposit.amount - deposit.receivedAmount
                                    Text(numberFormat.format(remaining), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = StatusConnected, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Username: ${depositConfig?.account?.username ?: "..."}", fontSize = 10.sp, color = TextMuted.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                        }

                        // ── Progress bar ──
                        if (deposit.receivedAmount > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceBorder.copy(alpha = 0.3f))
                                    .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Received", fontSize = 11.sp, color = TextMuted)
                                    Text(
                                        "${numberFormat.format(deposit.receivedAmount)} / ${numberFormat.format(deposit.amount)}",
                                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace, color = Accent,
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val progress = (deposit.receivedAmount.toFloat() / deposit.amount.toFloat()).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(SurfaceBorder),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Accent),
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Open Discord
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentDim.copy(alpha = 0.3f))
                                .border(1.dp, Accent.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .clickable {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://discord.com/channels/808935495543160852/1247649796311875634"),
                                    )
                                    context.startActivity(intent)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("# bakery", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("Open in Discord", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Accent.copy(alpha = 0.7f))
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Countdown
                        CountdownBanner(expiresAt = deposit.expiresAt)

                        Spacer(modifier = Modifier.height(14.dp))

                        // Refresh + Cancel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(label = "Refresh", color = Accent, onClick = onRefresh)
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(label = "Cancel", color = StatusError, onClick = onCancel)
                            }
                        }
                    }
                }
            }
        },
    )
}

// ── Withdraw flow ──

/** Server-side cap on the player's in-game coin total (Doughnate rejects overflow). */
private const val GAME_COIN_CAP: Long = 2_000_000

@Composable
private fun WithdrawFlow(
    amount: String,
    onAmountChange: (String) -> Unit,
    withdraw: WithdrawUiState,
    gameBalance: Long?,
    casinoBalance: Long?,
    gameBalanceLoading: Boolean,
    onRefreshGameBalance: () -> Unit,
    onConfirm: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val parsedAmount = amount.toLongOrNull()
    // Max withdrawable = min(casino balance, 2M − current game balance). If either
    // is unknown we fall back to the casino balance alone (still validated server-side).
    val headroom = gameBalance?.let { (GAME_COIN_CAP - it).coerceAtLeast(0L) }
    val maxWithdrawable = when {
        casinoBalance != null && headroom != null -> minOf(casinoBalance, headroom)
        casinoBalance != null -> casinoBalance
        headroom != null -> headroom
        else -> null
    }
    val withinMax = parsedAmount != null && parsedAmount > 0 &&
        (maxWithdrawable == null || parsedAmount <= maxWithdrawable)
    val overLimit = parsedAmount != null && maxWithdrawable != null && parsedAmount > maxWithdrawable
    val isValid = withinMax

    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) {
        WithdrawHelpDialog(onDismiss = { showHelp = false })
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Withdraw Breads", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = StatusConnected)
                IconButton(onClick = { showHelp = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.HelpOutline,
                        contentDescription = "Withdraw help",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (withdraw.status != "pending") {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            // ── Completed ──
            withdraw.status == "completed" -> {
                StatusBanner(
                    icon = Icons.Outlined.CheckCircle,
                    color = StatusConnected,
                    title = "Withdrawal complete!",
                    subtitle = "Breads have been sent to your game account.",
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledButton(label = "Done", color = StatusConnected, onClick = onCancel)
            }

            // ── Failed ──
            withdraw.status == "failed" -> {
                StatusBanner(
                    icon = Icons.Outlined.ErrorOutline,
                    color = StatusError,
                    title = "Withdrawal failed",
                    subtitle = "Your balance has been refunded. Try again later.",
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledButton(label = "Close", color = Accent, onClick = onCancel)
            }

            // ── Pending (queued) ──
            withdraw.status == "pending" -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusConnecting.copy(alpha = 0.08f))
                        .border(1.dp, StatusConnecting.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = StatusConnecting, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "Withdrawal queued",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = StatusConnecting,
                        )
                        Text(
                            "Position #${withdraw.position} in queue",
                            fontSize = 11.sp,
                            color = StatusConnecting.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            // ── Loading (submitting) ──
            withdraw.loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = StatusConnected, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Submitting withdrawal...", fontSize = 13.sp, color = TextMuted)
                }
            }

            // ── Amount input ──
            else -> {
                // Max withdrawable header — updates after onRefreshGameBalance returns.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            gameBalanceLoading && maxWithdrawable == null -> "Checking game balance..."
                            maxWithdrawable != null -> "Max withdrawable: ${"%,d".format(maxWithdrawable)}"
                            else -> "Max withdrawable: -"
                        },
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                    if (maxWithdrawable != null && maxWithdrawable > 0) {
                        Text(
                            "Use max",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusConnected,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onAmountChange(maxWithdrawable.toString()) }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { new -> onAmountChange(new.filter { it.isDigit() }) },
                    label = { Text("Amount") },
                    placeholder = { Text("Enter amount...") },
                    leadingIcon = {
                        AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    singleLine = true,
                    isError = overLimit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StatusConnected,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedLabelColor = StatusConnected,
                        cursorColor = StatusConnected,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (overLimit && maxWithdrawable != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Would push your game balance past ${"%,d".format(GAME_COIN_CAP)}. The max you can withdraw right now is ${"%,d".format(maxWithdrawable)}.",
                        fontSize = 11.sp,
                        color = StatusError,
                    )
                } else if (withdraw.error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(withdraw.error, fontSize = 11.sp, color = StatusError)
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Breads will be sent to your game account via /doughnate.",
                    fontSize = 11.sp,
                    color = TextMuted,
                )

                Spacer(modifier = Modifier.height(12.dp))
                FilledButton(
                    label = "Withdraw",
                    color = StatusConnected,
                    enabled = isValid,
                    onClick = { parsedAmount?.let { onConfirm(it) } },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(label = "Cancel", color = TextMuted, onClick = onCancel)
            }
        }
    }
}

@Composable
private fun WithdrawHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
        title = { Text("About withdrawals", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                "Breads are sent to your in-game account via the /doughnate command.\n\n" +
                    "/doughnate refuses to deliver if your new in-game balance would exceed " +
                    "${"%,d".format(GAME_COIN_CAP)}. " +
                    "The app caps the withdraw amount to what can actually be delivered. " +
                    "Max = min(casino balance, ${"%,d".format(GAME_COIN_CAP)} − your game balance).\n\n" +
                    "If your balance in-game grows after this dialog opens, tap the Use max shortcut " +
                    "to refresh the cap.",
                fontSize = 12.sp,
                color = TextMuted,
            )
        },
    )
}

// ── Countdown banner ──

@Composable
private fun CountdownBanner(expiresAt: String) {
    var secondsLeft by remember { mutableIntStateOf(300) }

    LaunchedEffect(expiresAt) {
        while (true) {
            val expires = try { Instant.parse(expiresAt) } catch (_: Exception) { null }
            secondsLeft = if (expires != null) {
                maxOf(0, ChronoUnit.SECONDS.between(Instant.now(), expires).toInt())
            } else 0
            if (secondsLeft <= 0) break
            delay(1_000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StatusConnecting.copy(alpha = 0.08f))
            .border(1.dp, StatusConnecting.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = null, tint = StatusConnecting, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Waiting for deposit...", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = StatusConnecting)
            Text(
                text = "${secondsLeft / 60}:${"%02d".format(secondsLeft % 60)} remaining",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = StatusConnecting.copy(alpha = 0.7f),
            )
        }
    }
}

// ── Shared UI components ──

@Composable
private fun StatusBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
            Text(subtitle, fontSize = 11.sp, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun FilledButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) color else TextMuted.copy(alpha = 0.3f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) SurfaceDark else TextMuted)
    }
}

@Composable
private fun OutlinedButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color.copy(alpha = 0.8f))
    }
}
