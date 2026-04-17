package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.MinesUiState
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

private val GemSpriteUrl = MgApi.plantSpriteUrl("Starweaver")
private val MineSpriteUrl = MgApi.lockSpriteUrl

private val GemColor = Color(0xFF22D3EE)
private val MineColor = StatusError
private val SafeRevealedBg = GemColor.copy(alpha = 0.15f)
private val MineBg = MineColor.copy(alpha = 0.15f)
private val HiddenBg = Color(0xFF1E2A3A)
private val GoldBright = Color(0xFFFFD700)

// Streak color progression
private fun streakColor(revealCount: Int): Color = when {
    revealCount >= 15 -> Color(0xFFFF6B6B) // danger red-pink
    revealCount >= 10 -> GoldBright         // gold
    revealCount >= 6 -> Color(0xFFFFA500)   // orange
    revealCount >= 3 -> StatusConnected     // green
    else -> GemColor                         // cyan
}

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
    val sound = rememberSoundManager()

    GameHeader(title = "Mines", casinoBalance = casinoBalance, onBack = { onReset(); onBack() })

    var lastAmount by remember { mutableStateOf("") }
    var lastMineCount by remember { mutableIntStateOf(5) }

    // Board shake on mine hit
    val boardShakeX = remember { Animatable(0f) }
    val boardShakeY = remember { Animatable(0f) }

    // Cascade reveal tracking for game-over mine reveal
    var cascadeRevealed by remember { mutableStateOf(emptySet<Int>()) }
    var showPopup by remember { mutableStateOf(false) }

    // Sound on reveal
    var prevRevealedCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.revealed.size) {
        val count = state.revealed.size
        if (count > prevRevealedCount) {
            sound.play(Sfx.REVEAL)
        }
        if (count >= 8 && prevRevealedCount < 8) {
            // Start alarm-like tension at high streaks
            sound.play(Sfx.ALARM, 0.3f)
        }
        prevRevealedCount = count
    }

    // Game over effects
    LaunchedEffect(state.gameOver) {
        if (state.gameOver) {
            showPopup = false
            if (state.won == true) {
                sound.play(Sfx.CASHOUT)
                delay(300)
                showPopup = true
            } else {
                // Board shake
                repeat(6) { i ->
                    val intensity = (6 - i) * 4f
                    boardShakeX.animateTo(intensity * (if (i % 2 == 0) 1 else -1), tween(40))
                }
                boardShakeX.animateTo(0f, tween(40))
                boardShakeY.snapTo(-3f)
                boardShakeY.animateTo(0f, tween(100))

                sound.play(Sfx.LOSE)

                // Cascade reveal mines one by one
                cascadeRevealed = emptySet()
                state.mines.forEachIndexed { idx, minePos ->
                    delay(40L + idx * 30L)
                    cascadeRevealed = cascadeRevealed + minePos
                    sound.play(Sfx.BUTTON, 0.3f)
                }

                delay(200)
                showPopup = true
            }
        } else {
            cascadeRevealed = emptySet()
            showPopup = false
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (!state.active && !state.gameOver) {
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
        // Board with shake + particle overlay
        Box {
            Column(
                modifier = Modifier.graphicsLayer {
                    translationX = boardShakeX.value
                    translationY = boardShakeY.value
                },
            ) {
                MinesBoard(
                    state = state,
                    cascadeRevealed = cascadeRevealed,
                    onReveal = onReveal,
                )
            }

        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!state.gameOver) {
            // Streak banner when doing well
            val revealCount = state.revealed.size
            if (revealCount >= 3) {
                val sColor = streakColor(revealCount)
                val infiniteTransition = rememberInfiniteTransition(label = "streakPulse")
                val streakPulse by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "streakScale",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = streakPulse; scaleY = streakPulse }
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(sColor.copy(alpha = 0.15f), sColor.copy(alpha = 0.05f), sColor.copy(alpha = 0.15f)),
                            ),
                        )
                        .border(1.dp, sColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$revealCount safe reveals!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = sColor,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            MinesInfoBar(state = state)
            Spacer(modifier = Modifier.height(8.dp))

            // Cashout button with escalating glow
            val canCashout = state.revealed.isNotEmpty() && !state.loading
            val cashoutColor = streakColor(revealCount)
            val infiniteTransition = rememberInfiniteTransition(label = "minesCashout")
            val cashoutPulse by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (canCashout) 1.04f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "minesCashoutPulse",
            )

            Box(contentAlignment = Alignment.Center) {
                // Glow behind cashout button
                if (canCashout && revealCount >= 3) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        drawRoundRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    cashoutColor.copy(alpha = 0.2f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width * 0.5f,
                            ),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { scaleX = cashoutPulse; scaleY = cashoutPulse }
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (canCashout) Brush.horizontalGradient(
                                listOf(cashoutColor, cashoutColor.copy(alpha = 0.85f)),
                            ) else Brush.horizontalGradient(
                                listOf(TextMuted.copy(alpha = 0.3f), TextMuted.copy(alpha = 0.3f)),
                            ),
                        )
                        .clickable(enabled = canCashout) { onCashout() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val text = if (state.revealed.isNotEmpty())
                        "Cashout ${numberFormat.format(state.currentPayout)} (x${String.format("%.2f", state.currentMultiplier)})"
                    else "Reveal a cell first"
                    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (canCashout) SurfaceDark else TextMuted)
                }
            }
        }
    }

    if (state.gameOver) {
        ResultPopup(
            visible = showPopup,
            won = state.won == true,
            title = if (state.won == true) "You Won!" else "Locked!",
            subtitle = if (state.won == true) "x${String.format("%.2f", state.currentMultiplier)}" else null,
            bet = state.bet,
            payout = state.payout,
            onReplay = {
                val lastBet = state.bet
                val lastMines = state.mineCount
                onReset()
                onStart(lastBet, lastMines)
            },
            onBack = { onReset() },
        )
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

            BetInput(amount = amount, onAmountChange = { amount = it }, balance = balance, maxBet = 25_000)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Mines", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text("$mineCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MineColor)
            }
            Slider(
                value = mineCount.toFloat(),
                onValueChange = { mineCount = it.toInt() },
                valueRange = 1f..24f,
                steps = 22,
                colors = SliderDefaults.colors(
                    thumbColor = MineColor, activeTrackColor = MineColor,
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
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = if (canStart) SurfaceDark else TextMuted,
                )
            }
        }
    }
}

// ── Board ──

@Composable
private fun MinesBoard(
    state: MinesUiState,
    cascadeRevealed: Set<Int>,
    onReveal: (Int) -> Unit,
) {
    // Danger glow around board based on revealed count
    val revealCount = state.revealed.size
    val dangerColor = streakColor(revealCount)

    Box {
        // Glow canvas behind the board
        if (state.active && revealCount >= 2) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .padding(0.dp),
            ) {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dangerColor.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.width * 0.6f,
                    ),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f),
                )
            }
        }

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
                    val isMineRevealed = if (state.gameOver && state.won != true) {
                        pos in cascadeRevealed
                    } else {
                        pos in state.mines
                    }
                    MineCell(
                        pos = pos,
                        isRevealed = pos in state.revealed,
                        isMine = isMineRevealed,
                        isMineActual = pos in state.mines,
                        gameOver = state.gameOver,
                        revealCount = revealCount,
                        onReveal = onReveal,
                    )
                }
            }
        }
    }
}

@Composable
private fun MineCell(
    pos: Int,
    isRevealed: Boolean,
    isMine: Boolean,
    isMineActual: Boolean,
    gameOver: Boolean,
    revealCount: Int,
    onReveal: (Int) -> Unit,
) {
    val isClickable = !isRevealed && !gameOver && !isMineActual

    // Fast flip animation
    val flipRotation = remember { Animatable(0f) }
    var hasFlipped by remember { mutableStateOf(false) }

    LaunchedEffect(isRevealed, isMine) {
        if ((isRevealed || isMine) && !hasFlipped) {
            hasFlipped = true
            flipRotation.snapTo(0f)
            flipRotation.animateTo(180f, tween(180))
        }
    }

    // Reset flip state when game resets
    LaunchedEffect(gameOver, isRevealed, isMine) {
        if (!gameOver && !isRevealed && !isMine) {
            hasFlipped = false
            flipRotation.snapTo(0f)
        }
    }

    val showBack = flipRotation.value > 90f

    val bgColor = when {
        isMine && showBack -> MineBg
        isRevealed && showBack -> SafeRevealedBg
        else -> HiddenBg
    }
    val borderColor = when {
        isMine && showBack -> MineColor.copy(alpha = 0.6f)
        isRevealed && showBack -> GemColor.copy(alpha = 0.4f)
        else -> SurfaceBorder
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                rotationY = flipRotation.value
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(enabled = isClickable) { onReveal(pos) },
        contentAlignment = Alignment.Center,
    ) {
        if (showBack) {
            // Revealed content (mirrored to account for rotationY)
            Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                when {
                    isMine -> {
                        AsyncImage(
                            model = MineSpriteUrl, contentDescription = "Mine",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    isRevealed -> {
                        AsyncImage(
                            model = GemSpriteUrl, contentDescription = "Gem",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        } else {
            if (!gameOver) {
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

// ── Info bar ──

@Composable
private fun MinesInfoBar(state: MinesUiState) {
    val revealCount = state.revealed.size
    val multColor = streakColor(revealCount)

    // Multiplier pulse
    val infiniteTransition = rememberInfiniteTransition(label = "multPulse")
    val multScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (revealCount >= 3) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "multPulseScale",
    )

    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            InfoCell("Bet", numberFormat.format(state.bet), TextPrimary)
            InfoCell(
                "Payout",
                numberFormat.format(state.currentPayout),
                multColor,
                scale = if (revealCount >= 3) multScale else 1f,
            )
            InfoCell("Next", "x${String.format("%.2f", state.nextMultiplier)}", GemColor)
            InfoCell("Safe", "${state.safeRemaining}", if (state.safeRemaining <= 3) MineColor else TextMuted)
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String, color: Color, scale: Float = 1f) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Text(label, fontSize = 10.sp, color = TextMuted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color)
    }
}
