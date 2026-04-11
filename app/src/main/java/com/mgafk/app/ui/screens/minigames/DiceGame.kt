package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.data.repository.DiceResponse
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DiceGame(
    casinoBalance: Long?,
    result: DiceResponse?,
    loading: Boolean,
    error: String?,
    onPlay: (amount: Long, target: Int, direction: String) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onResultShown: () -> Unit = {},
) {
    val sound = rememberSoundManager()
    var amount by remember { mutableStateOf("") }
    var target by remember { mutableFloatStateOf(50f) }
    var direction by remember { mutableStateOf("over") }
    var animating by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var showBanner by remember { mutableStateOf(false) }

    // Displayed number during roll (slot-style vertical scroll)
    var displayedRoll by remember { mutableIntStateOf(50) }
    val slotScrollY = remember { Animatable(0f) }

    // Circle
    val circleScale = remember { Animatable(1f) }
    val circleGlowAlpha = remember { Animatable(0f) }

    // Bar marker
    val barPosition = remember { Animatable(0.5f) }

    // Shake
    val shakeX = remember { Animatable(0f) }

    LaunchedEffect(result) {
        if (result != null && !showResult) {
            animating = true
            showBanner = false
            circleScale.snapTo(1f)
            circleGlowAlpha.snapTo(0f)
            slotScrollY.snapTo(0f)

            sound.play(Sfx.DICE_ROLL)
            sound.play(Sfx.SLOTS_SPINNING, 0.4f, loop = true)

            // Gentle shake during roll
            launch {
                while (animating) {
                    shakeX.animateTo((-3..3).random().toFloat(), tween(80))
                }
                shakeX.animateTo(0f, tween(60))
            }

            // Sweep bar position randomly during roll
            launch {
                while (animating) {
                    barPosition.animateTo(
                        (5..95).random() / 100f,
                        tween(250, easing = FastOutSlowInEasing),
                    )
                }
            }

            // Slot-style number scroll — fast then slowing down
            var delayMs = 50L
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 1200) {
                // Scroll animation per tick
                launch {
                    slotScrollY.snapTo(-30f)
                    slotScrollY.animateTo(0f, tween(delayMs.toInt().coerceAtLeast(40)))
                }
                displayedRoll = (1..100).random()
                delay(delayMs)
                delayMs = (delayMs * 1.12f).toLong().coerceAtMost(200)
            }

            // Settle toward final value
            val nearValues = listOf(
                (result.roll + (-4..4).random()).coerceIn(1, 100),
                result.roll,
            )
            for (value in nearValues) {
                launch {
                    slotScrollY.snapTo(-20f)
                    slotScrollY.animateTo(0f, tween(200))
                }
                displayedRoll = value
                launch { barPosition.animateTo(value / 100f, tween(250, easing = FastOutSlowInEasing)) }
                delay(250)
            }

            // Stop spinning sound
            sound.stop(Sfx.SLOTS_SPINNING)

            // Final bar snap
            barPosition.animateTo(result.roll / 100f, tween(150))

            // Result impact
            val won = result.won
            launch {
                circleScale.snapTo(0.85f)
                circleScale.animateTo(1.15f, tween(120))
                circleScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            launch {
                circleGlowAlpha.snapTo(0.8f)
                circleGlowAlpha.animateTo(0.3f, tween(500))
            }

            sound.play(Sfx.REVEAL)
            delay(250)

            showResult = true
            animating = false
            sound.play(if (won) Sfx.WIN else Sfx.LOSE)
            onResultShown()

            delay(250)
            showBanner = true
        }
    }

    val targetInt = target.roundToInt()
    val winChance = if (direction == "over") 100 - targetInt else targetInt - 1
    val multiplier = if (winChance > 0) (96.0 / winChance) else 0.0

    GameHeader(title = "Dice", casinoBalance = casinoBalance, onBack = { onReset(); onBack() })

    Spacer(modifier = Modifier.height(8.dp))

    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                // Animating or result
                (animating || showResult) && result != null -> {
                    Spacer(modifier = Modifier.height(16.dp))

                    if (animating) {
                        Text("Rolling...", fontSize = 14.sp, color = TextMuted)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val rollColor = when {
                        animating -> Accent
                        result.won -> StatusConnected
                        else -> StatusError
                    }

                    // Circle with glow
                    Box(contentAlignment = Alignment.Center) {
                        // Glow behind
                        if (circleGlowAlpha.value > 0f && showResult) {
                            Canvas(modifier = Modifier.size(130.dp)) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            rollColor.copy(alpha = circleGlowAlpha.value),
                                            Color.Transparent,
                                        ),
                                    ),
                                    radius = 65f,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = circleScale.value
                                    scaleY = circleScale.value
                                    translationX = shakeX.value
                                }
                                .size(110.dp)
                                .clip(RoundedCornerShape(55.dp))
                                .background(rollColor.copy(alpha = 0.12f))
                                .border(3.dp, rollColor.copy(alpha = 0.5f), RoundedCornerShape(55.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Slot-style vertical scroll number
                            Box(
                                modifier = Modifier
                                    .size(80.dp, 50.dp)
                                    .clipToBounds(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$displayedRoll",
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = rollColor,
                                    modifier = Modifier.offset {
                                        IntOffset(0, slotScrollY.value.toInt())
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar
                    DiceProgressBar(
                        roll = displayedRoll,
                        target = result.target,
                        direction = result.direction,
                        rolling = animating,
                        smoothPosition = barPosition.value,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )

                    if (showResult) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Roll ${result.direction} ${result.target}",
                            fontSize = 13.sp, color = TextMuted,
                        )
                        Text(
                            "${result.winChance}% chance  |  x${"%.2f".format(result.multiplier)}",
                            fontSize = 12.sp, color = TextMuted,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Loading
                loading -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Accent, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Placing bet...", fontSize = 13.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Betting UI
                else -> {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Direction toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        DirectionButton("Roll Over", direction == "over") { direction = "over" }
                        DirectionButton("Roll Under", direction == "under") { direction = "under" }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Target display
                    Text(
                        "Target: $targetInt",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = target,
                        onValueChange = { target = it },
                        valueRange = 2f..99f,
                        steps = 96,
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent,
                            inactiveTrackColor = SurfaceBorder,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Preview bar
                    DiceProgressBar(
                        roll = null,
                        target = targetInt,
                        direction = direction,
                        rolling = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        InfoChip("Win Chance", "$winChance%")
                        InfoChip("Multiplier", "x${"%.2f".format(multiplier)}")
                        InfoChip(
                            "Profit",
                            if (amount.toLongOrNull() != null && amount.toLongOrNull()!! > 0)
                                numberFormat.format(((amount.toLong() * multiplier) - amount.toLong()).toLong())
                            else "\u2014",
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    BetInput(amount = amount, onAmountChange = { amount = it }, balance = casinoBalance)

                    if (error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error, fontSize = 11.sp, color = StatusError)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val parsedAmount = amount.toLongOrNull()
                    val canPlay = parsedAmount != null && parsedAmount > 0 && winChance > 0
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canPlay) Accent else TextMuted.copy(alpha = 0.3f))
                            .clickable(enabled = canPlay) {
                                if (parsedAmount != null) onPlay(parsedAmount, targetInt, direction)
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Roll!", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (canPlay) SurfaceDark else TextMuted,
                        )
                    }
                }
            }
        }
    }

    if (result != null) {
        ResultPopup(
            visible = showBanner,
            won = result.won,
            title = if (result.won) "You Won!" else "You Lost",
            subtitle = "Roll ${result.direction} ${result.target}  |  x${"%.2f".format(result.multiplier)}",
            bet = result.bet,
            payout = result.payout,
            onReplay = {
                showResult = false; showBanner = false; onReset()
                onPlay(result.bet, result.target, result.direction)
            },
            onBack = { showResult = false; showBanner = false; onReset() },
        )
    }
}

// ── Dice progress bar ──

@Composable
private fun DiceProgressBar(
    roll: Int?,
    target: Int,
    direction: String,
    rolling: Boolean,
    smoothPosition: Float? = null,
    modifier: Modifier = Modifier,
) {
    val animatedRollPos by animateFloatAsState(
        targetValue = smoothPosition ?: ((roll ?: -1) / 100f),
        animationSpec = if (smoothPosition != null) tween(50) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "rollPos",
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("0", fontSize = 9.sp, color = TextMuted.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
            Text("25", fontSize = 9.sp, color = TextMuted.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
            Text("50", fontSize = 9.sp, color = TextMuted.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
            Text("75", fontSize = 9.sp, color = TextMuted.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
            Text("100", fontSize = 9.sp, color = TextMuted.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(2.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            val w = size.width
            val h = size.height
            val barY = h * 0.55f
            val barH = 14f
            val cornerRadius = CornerRadius(barH / 2, barH / 2)

            // Background bar
            drawRoundRect(
                color = Color.White.copy(alpha = 0.08f),
                topLeft = Offset(0f, barY - barH / 2),
                size = Size(w, barH),
                cornerRadius = cornerRadius,
            )

            // Win zone
            val targetFraction = target / 100f
            if (direction == "over") {
                val startX = targetFraction * w
                drawRoundRect(
                    color = StatusConnected.copy(alpha = 0.25f),
                    topLeft = Offset(startX, barY - barH / 2),
                    size = Size(w - startX, barH),
                    cornerRadius = cornerRadius,
                )
            } else {
                val endX = targetFraction * w
                drawRoundRect(
                    color = StatusConnected.copy(alpha = 0.25f),
                    topLeft = Offset(0f, barY - barH / 2),
                    size = Size(endX, barH),
                    cornerRadius = cornerRadius,
                )
            }

            // Lose zone
            if (direction == "over") {
                val endX = targetFraction * w
                drawRoundRect(
                    color = StatusError.copy(alpha = 0.12f),
                    topLeft = Offset(0f, barY - barH / 2),
                    size = Size(endX, barH),
                    cornerRadius = cornerRadius,
                )
            } else {
                val startX = targetFraction * w
                drawRoundRect(
                    color = StatusError.copy(alpha = 0.12f),
                    topLeft = Offset(startX, barY - barH / 2),
                    size = Size(w - startX, barH),
                    cornerRadius = cornerRadius,
                )
            }

            // Target line with triangle
            val targetX = targetFraction * w
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(targetX, barY - barH / 2 - 2f),
                end = Offset(targetX, barY + barH / 2 + 2f),
                strokeWidth = 2.5f,
            )
            val triSize = 6f
            val triPath = Path().apply {
                moveTo(targetX - triSize, barY - barH / 2 - 4f)
                lineTo(targetX + triSize, barY - barH / 2 - 4f)
                lineTo(targetX, barY - barH / 2 + 1f)
                close()
            }
            drawPath(triPath, Color.White.copy(alpha = 0.7f))

            // Roll marker
            if (roll != null && animatedRollPos >= 0f) {
                val rollX = (animatedRollPos * w).coerceIn(8f, w - 8f)
                val won = if (direction == "over") roll > target else roll < target
                val markerColor = if (rolling) Accent else if (won) StatusConnected else StatusError

                // Glow
                drawCircle(
                    color = markerColor.copy(alpha = 0.3f),
                    radius = 18f,
                    center = Offset(rollX, barY),
                )
                drawCircle(
                    color = markerColor,
                    radius = 11f,
                    center = Offset(rollX, barY),
                )
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = Offset(rollX, barY),
                )
                drawCircle(
                    color = markerColor,
                    radius = 3.5f,
                    center = Offset(rollX, barY),
                )
            }
        }
    }
}

@Composable
private fun DirectionButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "dirScale",
    )
    val borderColor = if (selected) Accent else SurfaceBorder
    val bgColor = if (selected) Accent.copy(alpha = 0.12f) else Color.Transparent

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Accent else TextPrimary,
        )
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 10.sp, color = TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
    }
}
