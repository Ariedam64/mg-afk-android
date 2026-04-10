package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.ui.MinesUiState
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import java.text.NumberFormat
import java.util.Locale

private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

private const val GEM_SPRITE_URL = "https://mg-api.ariedam.fr/assets/sprites/plants/Starweaver.png"
private const val MINE_SPRITE_URL = "https://mg-api.ariedam.fr/assets/sprites/ui/Locked.png"

private val GemColor = Color(0xFF22D3EE)
private val MineColor = StatusError
private val SafeRevealedBg = GemColor.copy(alpha = 0.15f)
private val MineBg = MineColor.copy(alpha = 0.15f)
private val HiddenBg = Color(0xFF1E2A3A)

@Composable
fun MinesGame(
    casinoBalance: Long?,
    state: MinesUiState,
    onStart: (amount: Long, mineCount: Int) -> Unit,
    onReveal: (position: Int) -> Unit,
    onCashout: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    // Header
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onReset(); onBack() }) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Text("Mines", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.weight(1f))
        if (casinoBalance != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(numberFormat.format(casinoBalance), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = StatusConnected)
            }
        }
    }

    var lastAmount by remember { mutableStateOf("") }
    var lastMineCount by remember { mutableIntStateOf(5) }

    Spacer(modifier = Modifier.height(8.dp))

    if (!state.active && !state.gameOver) {
        // ── Setup screen ──
        MinesSetup(
            balance = casinoBalance,
            error = state.error,
            loading = state.loading,
            initialAmount = lastAmount,
            initialMineCount = lastMineCount,
            onStart = { amount, mines ->
                lastAmount = amount.toString()
                lastMineCount = mines
                onStart(amount, mines)
            },
        )
    } else {
        // ── Game board ──
        MinesBoard(state = state, onReveal = onReveal)

        Spacer(modifier = Modifier.height(8.dp))

        // ── Info bar ──
        if (!state.gameOver) {
            MinesInfoBar(state = state)
            Spacer(modifier = Modifier.height(8.dp))
            // Cashout button
            val canCashout = state.revealed.isNotEmpty() && !state.loading
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canCashout) StatusConnected else TextMuted.copy(alpha = 0.3f))
                    .clickable(enabled = canCashout) { onCashout() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                val text = if (state.revealed.isNotEmpty())
                    "Cashout ${numberFormat.format(state.currentPayout)} (x${String.format("%.2f", state.currentMultiplier)})"
                else "Reveal a cell first"
                Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (canCashout) SurfaceDark else TextMuted)
            }
        } else {
            // ── Game over result ──
            MinesResult(state = state)
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Accent)
                    .clickable { onReset() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Play Again", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SurfaceDark)
            }
        }
    }
}

// ── Setup ──

@Composable
private fun MinesSetup(
    balance: Long?,
    error: String?,
    loading: Boolean,
    initialAmount: String = "",
    initialMineCount: Int = 5,
    onStart: (Long, Int) -> Unit,
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var mineCount by remember { mutableIntStateOf(initialMineCount) }
    val parsedAmount = amount.toLongOrNull()
    val canStart = parsedAmount != null && parsedAmount > 0 && !loading

    AppCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Reveal cells and avoid the locks! Cash out anytime or risk it for a higher multiplier.",
                fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { new -> amount = new.filter { it.isDigit() } },
                label = { Text("Bet amount") },
                placeholder = { Text("Max 25,000") },
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

            // Quick bet
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("100", "500", "1000", "5000").forEach { preset ->
                    val isSelected = amount == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Accent.copy(alpha = 0.15f) else Accent.copy(alpha = 0.06f))
                            .border(1.dp, if (isSelected) Accent.copy(alpha = 0.4f) else Accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .clickable { amount = preset }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(preset, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isSelected) Accent else TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mine count slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Mines", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(
                    "$mineCount",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MineColor,
                )
            }
            Slider(
                value = mineCount.toFloat(),
                onValueChange = { mineCount = it.toInt() },
                valueRange = 1f..24f,
                steps = 22,
                colors = SliderDefaults.colors(
                    thumbColor = MineColor,
                    activeTrackColor = MineColor,
                    inactiveTrackColor = SurfaceBorder,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("1", fontSize = 10.sp, color = TextMuted)
                Text("Safe: ${25 - mineCount}", fontSize = 10.sp, color = GemColor)
                Text("24", fontSize = 10.sp, color = TextMuted)
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(error, fontSize = 11.sp, color = StatusError)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canStart) Accent else TextMuted.copy(alpha = 0.3f))
                    .clickable(enabled = canStart) { parsedAmount?.let { onStart(it, mineCount) } }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (loading) "Starting..." else "Start Game",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canStart) SurfaceDark else TextMuted,
                )
            }
        }
    }
}

// ── Board ──

@Composable
private fun MinesBoard(state: MinesUiState, onReveal: (Int) -> Unit) {
    AppCard {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(25) { pos ->
                val isRevealed = pos in state.revealed
                val isMine = pos in state.mines
                val isClickable = !isRevealed && !state.gameOver && !state.loading

                val bgColor by animateColorAsState(
                    targetValue = when {
                        isMine -> MineBg
                        isRevealed -> SafeRevealedBg
                        else -> HiddenBg
                    },
                    animationSpec = tween(300),
                    label = "cell_$pos",
                )
                val borderColor = when {
                    isMine -> MineColor.copy(alpha = 0.5f)
                    isRevealed -> GemColor.copy(alpha = 0.3f)
                    else -> SurfaceBorder
                }

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .clickable(enabled = isClickable) { onReveal(pos) },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isMine -> AsyncImage(model = MINE_SPRITE_URL, contentDescription = "Mine", modifier = Modifier.size(28.dp))
                        isRevealed -> AsyncImage(model = GEM_SPRITE_URL, contentDescription = "Gem", modifier = Modifier.size(28.dp))
                        state.gameOver -> {
                            // show unrevealed cells dim
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(TextMuted.copy(alpha = 0.3f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Info bar ──

@Composable
private fun MinesInfoBar(state: MinesUiState) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            InfoCell("Bet", numberFormat.format(state.bet), TextPrimary)
            InfoCell("Payout", numberFormat.format(state.currentPayout), StatusConnected)
            InfoCell("Next", "x${String.format("%.2f", state.nextMultiplier)}", GemColor)
            InfoCell("Safe", "${state.safeRemaining}", TextMuted)
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = TextMuted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color)
    }
}

// ── Result ──

@Composable
private fun MinesResult(state: MinesUiState) {
    val won = state.won == true
    val color = if (won) StatusConnected else StatusError

    AppCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.1f))
                .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (won) "You Won!" else "Locked!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (won) "+${numberFormat.format(state.payout)}" else "-${numberFormat.format(state.bet)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                    )
                }
                if (won && state.currentMultiplier > 0) {
                    Text(
                        "x${String.format("%.2f", state.currentMultiplier)}",
                        fontSize = 13.sp,
                        color = color.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
