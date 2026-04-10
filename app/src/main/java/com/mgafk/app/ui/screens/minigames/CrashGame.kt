package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.ui.CrashUiState
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
import kotlin.math.exp
import kotlin.math.ln

private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

@Composable
fun CrashGame(
    casinoBalance: Long?,
    state: CrashUiState,
    onStart: (amount: Long) -> Unit,
    onCashout: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val sound = rememberSoundManager()
    var amount by remember { mutableStateOf("") }
    var localMultiplier by remember { mutableDoubleStateOf(1.0) }
    var displayMultiplierNoise by remember { mutableDoubleStateOf(0.0) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var showBanner by remember { mutableStateOf(false) }
    // Noise history for the graph (list of time -> noise offset pairs)
    val noiseHistory = remember { mutableListOf<Pair<Long, Double>>() }

    // Shake on crash
    val crashShakeX = remember { Animatable(0f) }
    val crashShakeY = remember { Animatable(0f) }
    // Flash overlay alpha
    val flashAlpha = remember { Animatable(0f) }
    // Multiplier scale punch
    val multScale = remember { Animatable(1f) }

    // Animate multiplier locally with visual turbulence
    LaunchedEffect(state.active, state.crashed, state.cashedOut) {
        if (state.active && !state.crashed && !state.cashedOut && state.startTime > 0) {
            sound.play(Sfx.CRASH_RISING, 0.5f, loop = true)
            noiseHistory.clear()
            var currentNoise = 0.0
            var noiseTarget = 0.0
            var frameCount = 0
            while (true) {
                val now = System.currentTimeMillis()
                elapsedMs = now - state.startTime
                val realMult = exp(state.growthRate * elapsedMs)
                localMultiplier = realMult

                // Generate turbulence: every ~15 frames pick a new noise target
                if (frameCount % 15 == 0) {
                    // Noise amplitude scales with multiplier (bigger swings at higher values)
                    val amplitude = (realMult * 0.04).coerceIn(0.01, 0.8)
                    noiseTarget = (Math.random() * 2 - 1) * amplitude
                    // Occasionally create a bigger dip (10% chance)
                    if (Math.random() < 0.10 && realMult > 1.3) {
                        noiseTarget = -amplitude * 2.5
                    }
                }
                // Smoothly interpolate toward target noise
                currentNoise += (noiseTarget - currentNoise) * 0.12
                displayMultiplierNoise = currentNoise

                // Record for graph (sample every 5 frames to save memory)
                if (frameCount % 5 == 0) {
                    noiseHistory.add(elapsedMs to currentNoise)
                    // Cap history size
                    if (noiseHistory.size > 500) noiseHistory.removeAt(0)
                }

                frameCount++
                delay(33)
            }
        }
    }

    // On crash: shake + red flash
    LaunchedEffect(state.crashed) {
        if (state.crashed) {
            localMultiplier = state.crashPoint
            displayMultiplierNoise = 0.0
            showBanner = false
            sound.stop(Sfx.CRASH_RISING)
            sound.play(Sfx.JACKPOT)

            // Red flash
            launch {
                flashAlpha.animateTo(0.3f, tween(80))
                flashAlpha.animateTo(0f, tween(400))
            }

            // Shake
            repeat(6) {
                val intensity = (6 - it) * 4f
                crashShakeX.animateTo((-intensity..intensity).random().toFloat(), tween(40))
                crashShakeY.animateTo((-intensity * 0.5f..intensity * 0.5f).random().toFloat(), tween(40))
            }
            crashShakeX.animateTo(0f, tween(50))
            crashShakeY.animateTo(0f, tween(50))

            delay(400)
            showBanner = true
        }
    }

    // On cashout: green pulse
    LaunchedEffect(state.cashedOut) {
        if (state.cashedOut) {
            localMultiplier = state.multiplier
            displayMultiplierNoise = 0.0
            showBanner = false
            sound.stop(Sfx.CRASH_RISING)
            sound.play(Sfx.CASHOUT)
            multScale.animateTo(1.3f, tween(200))
            multScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            delay(300)
            showBanner = true
        }
    }

    val gameOver = state.crashed || state.cashedOut

    // Pulsing cashout button
    val infiniteTransition = rememberInfiniteTransition(label = "cashoutPulse")
    val cashoutPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cashoutScale",
    )

    // Header
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onReset(); onBack() }) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Text("Crash", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
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

    // Wrap in a box for crash shake + flash overlay
    Box {
        AppCard(
            modifier = Modifier.graphicsLayer {
                translationX = crashShakeX.value
                translationY = crashShakeY.value
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    state.active -> {
                        val displayMultiplier = if (gameOver) {
                            if (state.crashed) state.crashPoint else state.multiplier
                        } else (localMultiplier + displayMultiplierNoise).coerceAtLeast(1.0)

                        val multiplierColor by animateColorAsState(
                            targetValue = when {
                                state.crashed -> StatusError
                                state.cashedOut -> StatusConnected
                                displayMultiplier >= 10.0 -> Color(0xFFFF4444)
                                displayMultiplier >= 5.0 -> Color(0xFFFF6B6B)
                                displayMultiplier >= 2.0 -> Color(0xFFFFA500)
                                else -> StatusConnected
                            },
                            animationSpec = tween(300),
                            label = "multColor",
                        )

                        // Dynamic font size based on multiplier
                        val fontSize by animateFloatAsState(
                            targetValue = when {
                                displayMultiplier >= 10.0 -> 56f
                                displayMultiplier >= 5.0 -> 52f
                                displayMultiplier >= 2.0 -> 50f
                                else -> 48f
                            },
                            animationSpec = tween(500),
                            label = "fontSize",
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Graph
                        CrashGraph(
                            elapsedMs = if (gameOver) elapsedMs else (System.currentTimeMillis() - state.startTime),
                            growthRate = state.growthRate,
                            crashed = state.crashed,
                            cashedOut = state.cashedOut,
                            noiseHistory = noiseHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceDark)
                                .padding(8.dp),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Big multiplier
                        Text(
                            "${"%.2f".format(displayMultiplier)}x",
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = multiplierColor,
                            modifier = Modifier.graphicsLayer {
                                scaleX = multScale.value
                                scaleY = multScale.value
                            },
                        )

                        if (state.crashed) {
                            Text("CRASHED!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StatusError)
                        } else if (state.cashedOut) {
                            Text("CASHED OUT!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = StatusConnected)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Bet & profit info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bet: ${numberFormat.format(state.bet)}", fontSize = 13.sp, color = TextMuted)
                            if (!gameOver) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    "Profit: ${numberFormat.format(((state.bet * displayMultiplier) - state.bet).toLong())}",
                                    fontSize = 13.sp, color = StatusConnected, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (gameOver) {
                            // Result banner
                            AnimatedVisibility(
                                visible = showBanner,
                                enter = scaleIn(
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    initialScale = 0.5f,
                                ) + fadeIn(),
                            ) {
                                val bannerColor = if (state.won) StatusConnected else StatusError
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
                                            if (state.won) "You Won!" else "You Lost",
                                            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = bannerColor,
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (state.won) "+${numberFormat.format(state.payout)}" else "-${numberFormat.format(state.bet)}",
                                                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace, color = bannerColor,
                                            )
                                        }
                                        if (state.crashed && !state.won) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Crashed at ${"%.2f".format(state.crashPoint)}x",
                                                fontSize = 12.sp, color = TextMuted,
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
                                                val lastBet = state.bet
                                                onReset()
                                                onStart(lastBet)
                                            }
                                            .padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("Replay ${numberFormat.format(state.bet)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SurfaceDark)
                                    }
                                }
                            }
                        } else {
                            // Pulsing cashout button
                            val cashoutAmount = (state.bet * localMultiplier).toLong()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        scaleX = cashoutPulse
                                        scaleY = cashoutPulse
                                    }
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                StatusConnected,
                                                StatusConnected.copy(alpha = 0.85f),
                                            ),
                                        ),
                                    )
                                    .clickable(enabled = !state.loading) { onCashout() }
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = SurfaceDark, strokeWidth = 3.dp)
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "CASHOUT",
                                            fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                            color = SurfaceDark,
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${numberFormat.format(cashoutAmount)} breads",
                                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                            color = SurfaceDark.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    state.loading -> {
                        Spacer(modifier = Modifier.height(32.dp))
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Accent, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Starting game...", fontSize = 13.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(32.dp))
                    }

                    // Betting UI
                    else -> {
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Accent.copy(alpha = 0.05f))
                                .border(1.dp, Accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Column {
                                Text(
                                    "The multiplier rises over time. Cashout before it crashes!",
                                    fontSize = 12.sp, color = TextMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    TimeChip("1s", "x1.16")
                                    TimeChip("5s", "x2.12")
                                    TimeChip("10s", "x4.48")
                                    TimeChip("20s", "x20.09")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

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

                        if (state.error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(state.error, fontSize = 11.sp, color = StatusError)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val parsedAmount = amount.toLongOrNull()
                        val canPlay = parsedAmount != null && parsedAmount > 0
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (canPlay) Accent else TextMuted.copy(alpha = 0.3f))
                                .clickable(enabled = canPlay) {
                                    if (parsedAmount != null) onStart(parsedAmount)
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Start!", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = if (canPlay) SurfaceDark else TextMuted,
                            )
                        }
                    }
                }
            }
        }

        // Red flash overlay on crash
        if (flashAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(StatusError.copy(alpha = flashAlpha.value)),
            )
        }
    }
}

// ── Crash graph with gradient fill ──

@Composable
private fun CrashGraph(
    elapsedMs: Long,
    growthRate: Double,
    crashed: Boolean,
    cashedOut: Boolean,
    noiseHistory: List<Pair<Long, Double>> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val lineColor = when {
        crashed -> StatusError
        cashedOut -> StatusConnected
        else -> StatusConnected
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 4f

        val totalMs = elapsedMs.coerceAtLeast(2000L).toFloat()
        // Find the peak multiplier including noise for Y scaling
        val basePeak = exp(growthRate * totalMs.toDouble()).toFloat()
        val noisePeak = noiseHistory.maxOfOrNull { exp(growthRate * it.first / 1000.0 * 1000).toFloat() + it.second.toFloat() } ?: 0f
        val maxMultiplier = maxOf(basePeak, noisePeak, 2f)
        val logMax = ln(maxMultiplier.toDouble()).toFloat()

        val linePath = Path()
        val fillPath = Path()
        val steps = 200
        var firstPoint = true
        var lastX = pad
        var lastY = h - pad

        // Build a noise lookup for quick interpolation
        fun noiseAt(timeMs: Float): Float {
            if (noiseHistory.isEmpty()) return 0f
            val idx = noiseHistory.indexOfLast { it.first <= timeMs.toLong() }
            if (idx < 0) return 0f
            if (idx >= noiseHistory.lastIndex) return noiseHistory.last().second.toFloat()
            val a = noiseHistory[idx]
            val b = noiseHistory[idx + 1]
            val frac = ((timeMs - a.first) / (b.first - a.first).coerceAtLeast(1L)).coerceIn(0f, 1f)
            return (a.second + (b.second - a.second) * frac).toFloat()
        }

        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * totalMs
            if (t > elapsedMs) break
            val baseMult = exp(growthRate * t.toDouble()).toFloat()
            val noise = noiseAt(t)
            val mult = (baseMult + noise).coerceAtLeast(1f)
            val x = pad + (t / totalMs) * (w - 2 * pad)
            val logMult = ln(mult.toDouble()).toFloat()
            val y = h - pad - (logMult / logMax) * (h - 2 * pad)

            if (firstPoint) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h - pad)
                fillPath.lineTo(x, y)
                firstPoint = false
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastX = x
            lastY = y
        }

        // Close fill path
        fillPath.lineTo(lastX, h - pad)
        fillPath.close()

        // Grid lines
        for (i in 1..3) {
            val y = h - pad - (i.toFloat() / 4f) * (h - 2 * pad)
            drawLine(Color.White.copy(alpha = 0.04f), Offset(pad, y), Offset(w - pad, y), 1f)
        }

        // Baseline
        drawLine(Color.White.copy(alpha = 0.08f), Offset(pad, h - pad), Offset(w - pad, h - pad), 1f)

        // Gradient fill under curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.2f), lineColor.copy(alpha = 0.02f)),
                startY = 0f,
                endY = h,
            ),
            style = Fill,
        )

        // Main curve
        drawPath(linePath, lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

        // Glow dot at tip
        if (!crashed) {
            drawCircle(lineColor.copy(alpha = 0.3f), radius = 8f, center = Offset(lastX, lastY))
            drawCircle(lineColor, radius = 4f, center = Offset(lastX, lastY))
        }

        // Crash X marker
        if (crashed) {
            drawCircle(StatusError.copy(alpha = 0.3f), radius = 12f, center = Offset(lastX, lastY))
            drawCircle(StatusError, radius = 6f, center = Offset(lastX, lastY))
            // X cross
            val crossSize = 5f
            drawLine(Color.White, Offset(lastX - crossSize, lastY - crossSize), Offset(lastX + crossSize, lastY + crossSize), 2f)
            drawLine(Color.White, Offset(lastX + crossSize, lastY - crossSize), Offset(lastX - crossSize, lastY + crossSize), 2f)
        }
    }
}

@Composable
private fun TimeChip(time: String, mult: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(time, fontSize = 10.sp, color = TextMuted)
        Text(mult, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
    }
}

private fun ClosedFloatingPointRange<Float>.random(): Float {
    return start + (Math.random() * (endInclusive - start)).toFloat()
}
