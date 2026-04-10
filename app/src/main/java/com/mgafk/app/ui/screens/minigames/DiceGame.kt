package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBounce
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

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
    var displayedRoll by remember { mutableIntStateOf(50) }
    var showBanner by remember { mutableStateOf(false) }

    // Animated circle scale (bounces on result)
    val circleScale = remember { Animatable(1f) }
    // Shake on rolling
    val shakeX = remember { Animatable(0f) }
    // Smooth bar position (0..1) animated with Animatable — no jumps
    val barPosition = remember { Animatable(0.5f) }

    LaunchedEffect(result) {
        if (result != null && !showResult) {
            animating = true
            showBanner = false
            circleScale.snapTo(0.8f)
            sound.play(Sfx.DICE_ROLL)

            // Phase 1: sweep the bar smoothly back and forth
            launch {
                while (animating) {
                    val nextPos = (5..95).random() / 100f
                    // Smooth tween to each new position — long enough to look fluid
                    barPosition.animateTo(nextPos, tween(400, easing = FastOutSlowInEasing))
                }
            }

            // Gentle shake during roll
            launch {
                while (animating) {
                    shakeX.animateTo((-4..4).random().toFloat(), tween(100))
                }
                shakeX.animateTo(0f, tween(80))
            }

            // Numbers slow down progressively
            var delayMs = 70L
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 2000) {
                displayedRoll = (1..100).random()
                delay(delayMs)
                delayMs = (delayMs * 1.1f).toLong().coerceAtMost(280)
            }

            // Phase 2: settle toward final value
            val steps = listOf(
                (result.roll + (-6..6).random()).coerceIn(1, 100),
                (result.roll + (-2..2).random()).coerceIn(1, 100),
                result.roll,
            )
            for (value in steps) {
                displayedRoll = value
                // Also smoothly move bar to this value
                launch { barPosition.animateTo(value / 100f, tween(300, easing = FastOutSlowInEasing)) }
                delay(300)
            }

            // Final position
            barPosition.animateTo(result.roll / 100f, tween(200))

            // Bounce scale in
            circleScale.animateTo(1.2f, tween(150))
            circleScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))

            delay(200)
            animating = false
            showResult = true
            sound.play(if (result.won) Sfx.WIN else Sfx.LOSE)
            onResultShown()

            delay(200)
            showBanner = true
        }
    }

    val targetInt = target.roundToInt()
    val winChance = if (direction == "over") 100 - targetInt else targetInt - 1
    val multiplier = if (winChance > 0) (96.0 / winChance) else 0.0

    // Header
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onReset(); onBack() }) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Text("Dice", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.weight(1f))
        if (casinoBalance != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    numberFormat.format(casinoBalance),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace, color = StatusConnected,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                // Animating
                animating && result != null -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Rolling...", fontSize = 14.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = circleScale.value
                                scaleY = circleScale.value
                                translationX = shakeX.value
                            }
                            .size(110.dp)
                            .clip(RoundedCornerShape(55.dp))
                            .background(Accent.copy(alpha = 0.12f))
                            .border(3.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(55.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$displayedRoll",
                            fontSize = 42.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = Accent,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Target bar preview during animation
                    DiceProgressBar(
                        roll = displayedRoll,
                        target = result.target,
                        direction = result.direction,
                        rolling = true,
                        smoothPosition = barPosition.value,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Result
                showResult && result != null -> {
                    Spacer(modifier = Modifier.height(16.dp))

                    val rollColor = if (result.won) StatusConnected else StatusError

                    // Result circle with bounce
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = circleScale.value
                                scaleY = circleScale.value
                            }
                            .size(110.dp)
                            .clip(RoundedCornerShape(55.dp))
                            .background(rollColor.copy(alpha = 0.12f))
                            .border(3.dp, rollColor.copy(alpha = 0.5f), RoundedCornerShape(55.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${result.roll}",
                            fontSize = 42.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = rollColor,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress bar showing where the roll landed
                    DiceProgressBar(
                        roll = result.roll,
                        target = result.target,
                        direction = result.direction,
                        rolling = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Roll ${result.direction} ${result.target}",
                        fontSize = 13.sp, color = TextMuted,
                    )
                    Text(
                        "${result.winChance}% chance  |  x${"%.2f".format(result.multiplier)}",
                        fontSize = 12.sp, color = TextMuted,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Win/Lose banner with scale-in
                    AnimatedVisibility(
                        visible = showBanner,
                        enter = scaleIn(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            initialScale = 0.5f,
                        ) + fadeIn(),
                    ) {
                        val bannerColor = if (result.won) StatusConnected else StatusError
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bannerColor.copy(alpha = 0.1f))
                                .border(1.dp, bannerColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (result.won) "You Won!" else "You Lost",
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = bannerColor,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (result.won) "+${numberFormat.format(result.payout)}" else "-${numberFormat.format(result.bet)}",
                                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace, color = bannerColor,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(
                        visible = showBanner,
                        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceBorder)
                                    .clickable { onReset(); onBack() }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Back", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Accent)
                                    .clickable {
                                        val lastBet = result.bet
                                        val lastTarget = result.target
                                        val lastDir = result.direction
                                        showResult = false; showBanner = false; onReset()
                                        onPlay(lastBet, lastTarget, lastDir)
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Replay ${numberFormat.format(result.bet)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SurfaceDark)
                            }
                        }
                    }
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

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { new -> amount = new.filter { it.isDigit() } },
                        label = { Text("Bet amount") },
                        placeholder = { Text("Max 30,000") },
                        leadingIcon = {
                            AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = SurfaceBorder,
                            focusedLabelColor = Accent, cursorColor = Accent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

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
}

// ── Dice progress bar showing 1-100 range, target line, and roll position ──

@Composable
private fun DiceProgressBar(
    roll: Int?,
    target: Int,
    direction: String,
    rolling: Boolean,
    smoothPosition: Float? = null,
    modifier: Modifier = Modifier,
) {
    // Use pre-animated smooth position if provided, otherwise animate from roll
    val animatedRollPos by animateFloatAsState(
        targetValue = smoothPosition ?: ((roll ?: -1) / 100f),
        animationSpec = if (smoothPosition != null) tween(50) else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "rollPos",
    )

    Column(modifier = modifier) {
        // Number labels row
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
            val barH = 12f
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
                    color = StatusConnected.copy(alpha = 0.2f),
                    topLeft = Offset(startX, barY - barH / 2),
                    size = Size(w - startX, barH),
                    cornerRadius = cornerRadius,
                )
            } else {
                val endX = targetFraction * w
                drawRoundRect(
                    color = StatusConnected.copy(alpha = 0.2f),
                    topLeft = Offset(0f, barY - barH / 2),
                    size = Size(endX, barH),
                    cornerRadius = cornerRadius,
                )
            }

            // Lose zone subtle
            if (direction == "over") {
                val endX = targetFraction * w
                drawRoundRect(
                    color = StatusError.copy(alpha = 0.1f),
                    topLeft = Offset(0f, barY - barH / 2),
                    size = Size(endX, barH),
                    cornerRadius = cornerRadius,
                )
            } else {
                val startX = targetFraction * w
                drawRoundRect(
                    color = StatusError.copy(alpha = 0.1f),
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
            // Small triangle above the target
            val triSize = 6f
            val triPath = Path().apply {
                moveTo(targetX - triSize, barY - barH / 2 - 4f)
                lineTo(targetX + triSize, barY - barH / 2 - 4f)
                lineTo(targetX, barY - barH / 2 + 1f)
                close()
            }
            drawPath(triPath, Color.White.copy(alpha = 0.7f))

            // Roll marker (animated)
            if (roll != null && animatedRollPos >= 0f) {
                val rollX = (animatedRollPos * w).coerceIn(8f, w - 8f)
                val won = if (direction == "over") roll > target else roll < target
                val markerColor = if (rolling) Accent else if (won) StatusConnected else StatusError

                // Glow
                drawCircle(
                    color = markerColor.copy(alpha = 0.25f),
                    radius = 16f,
                    center = Offset(rollX, barY),
                )
                // Outer circle
                drawCircle(
                    color = markerColor,
                    radius = 11f,
                    center = Offset(rollX, barY),
                )
                // Inner circle
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = Offset(rollX, barY),
                )
                // Inner colored dot
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
