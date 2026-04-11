package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutBounce
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import com.mgafk.app.data.repository.CoinflipResponse
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val COIN_HEADS_URL = "https://i.imgur.com/yPcQYDB.png"
private const val COIN_TAILS_URL = "https://i.imgur.com/J2gqn25.png"

// Gold palette for effects
private val GoldBright = Color(0xFFFFD700)
private val GoldDark = Color(0xFFB8860B)
private val GoldLight = Color(0xFFFFF8DC)

// Particle for sparkle/explosion effects
private data class CoinParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,       // 1.0 → 0.0
    var maxLife: Float,
    var size: Float,
    var color: Color,
    var rotation: Float = 0f,
    var rotationSpeed: Float = 0f,
)

@Composable
fun CoinFlipGame(
    casinoBalance: Long?,
    result: CoinflipResponse?,
    loading: Boolean,
    error: String?,
    onPlay: (amount: Long, choice: String) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onResultShown: () -> Unit = {},
) {
    val sound = rememberSoundManager()
    var amount by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf<String?>(null) }

    // Animation phases: idle → flipping → landing → result
    var phase by remember { mutableStateOf("idle") }
    var showBanner by remember { mutableStateOf(false) }

    // Coin animation values
    val coinRotation = remember { Animatable(0f) }
    val coinY = remember { Animatable(0f) }         // vertical arc
    val coinScale = remember { Animatable(1f) }
    val shadowScale = remember { Animatable(1f) }
    val glowAlpha = remember { Animatable(0f) }
    val shockwaveRadius = remember { Animatable(0f) }
    val shockwaveAlpha = remember { Animatable(0f) }


    // Particles
    var particles by remember { mutableStateOf(emptyList<CoinParticle>()) }
    var trailParticles by remember { mutableStateOf(emptyList<CoinParticle>()) }

    // Result glow color
    var resultGlowColor by remember { mutableStateOf(GoldBright) }

    // Particle tick
    LaunchedEffect(phase) {
        if (phase == "flipping" || phase == "landing") {
            while (true) {
                // Update trail particles
                trailParticles = trailParticles.mapNotNull { p ->
                    val decay = 0.016f / p.maxLife
                    val newLife = p.life - decay
                    if (newLife <= 0f) null
                    else p.copy(
                        x = p.x + p.vx,
                        y = p.y + p.vy,
                        life = newLife,
                        rotation = p.rotation + p.rotationSpeed,
                    )
                }
                // Update explosion particles
                particles = particles.mapNotNull { p ->
                    val decay = 0.016f / p.maxLife
                    val newLife = p.life - decay
                    if (newLife <= 0f) null
                    else p.copy(
                        x = p.x + p.vx,
                        y = p.y + p.vy,
                        vy = p.vy + 0.3f, // gravity
                        life = newLife,
                        rotation = p.rotation + p.rotationSpeed,
                    )
                }
                delay(16)
            }
        }
    }

    // Spawn trail sparkles during flip
    LaunchedEffect(phase) {
        if (phase == "flipping") {
            while (true) {
                val sparkle = CoinParticle(
                    x = 0f + Random.nextFloat() * 40f - 20f,
                    y = coinY.value + Random.nextFloat() * 20f - 10f,
                    vx = Random.nextFloat() * 2f - 1f,
                    vy = Random.nextFloat() * 1.5f + 0.5f,
                    life = 1f,
                    maxLife = 0.6f + Random.nextFloat() * 0.4f,
                    size = 2f + Random.nextFloat() * 4f,
                    color = listOf(GoldBright, GoldLight, GoldDark).random(),
                    rotation = Random.nextFloat() * 360f,
                    rotationSpeed = Random.nextFloat() * 10f - 5f,
                )
                trailParticles = trailParticles + sparkle
                delay((30L..60L).random())
            }
        }
    }

    // Main animation sequence
    LaunchedEffect(result) {
        if (result == null || phase == "result") return@LaunchedEffect

        phase = "flipping"
        showBanner = false
        particles = emptyList()
        trailParticles = emptyList()

        // Reset
        coinRotation.snapTo(0f)
        coinY.snapTo(0f)
        coinScale.snapTo(1f)
        shadowScale.snapTo(1f)
        glowAlpha.snapTo(0f)
        shockwaveRadius.snapTo(0f)
        shockwaveAlpha.snapTo(0f)

        sound.play(Sfx.CARD_FLIP)
        sound.play(Sfx.SLOTS_SPINNING, 0.5f, loop = true)

        // Glow ramps up during flip
        launch {
            glowAlpha.animateTo(0.7f, tween(1500))
        }

        // Shadow shrinks as coin goes up, grows on descent
        launch {
            shadowScale.animateTo(0.3f, tween(900, easing = EaseOutCubic))
            shadowScale.animateTo(1.2f, tween(900, easing = EaseInCubic))
        }

        // Coin goes up in an arc then comes down
        launch {
            coinY.animateTo(-160f, tween(900, easing = EaseOutCubic))
            coinY.animateTo(0f, tween(900, easing = EaseInCubic))
        }

        // Coin spins — land on correct face
        val totalSpins = 8
        val extraSpins = totalSpins * 360f
        val finalAngle = if (result.result == "heads") extraSpins else extraSpins + 180f

        coinRotation.animateTo(
            targetValue = finalAngle,
            animationSpec = tween(durationMillis = 1800, easing = { t ->
                // Fast start, natural deceleration at end
                val inv = 1f - t
                1f - inv * inv * inv
            }),
        )

        // === LANDING ===
        phase = "landing"
        sound.stop(Sfx.SLOTS_SPINNING)
        sound.play(Sfx.REVEAL)

        // Spawn explosion particles
        val explosionParticles = List(30) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 2f + Random.nextFloat() * 6f
            CoinParticle(
                x = 0f,
                y = 0f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 3f,
                life = 1f,
                maxLife = 0.5f + Random.nextFloat() * 0.5f,
                size = 3f + Random.nextFloat() * 5f,
                color = listOf(GoldBright, GoldLight, GoldDark, Color.White).random(),
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = Random.nextFloat() * 15f - 7.5f,
            )
        }
        particles = explosionParticles

        // Bounce effect
        launch {
            coinScale.snapTo(0.6f)
            coinScale.animateTo(1.15f, tween(100))
            coinScale.animateTo(0.95f, tween(80))
            coinScale.animateTo(1.02f, tween(60))
            coinScale.animateTo(1f, tween(50))
        }

        // Shockwave ring
        launch {
            shockwaveAlpha.snapTo(0.6f)
            shockwaveRadius.snapTo(0f)
            launch { shockwaveRadius.animateTo(120f, tween(400, easing = EaseOutCubic)) }
            delay(200)
            shockwaveAlpha.animateTo(0f, tween(200))
        }

        // Result glow pulse
        resultGlowColor = if (result.won) StatusConnected else StatusError
        launch {
            glowAlpha.snapTo(1f)
            glowAlpha.animateTo(0.4f, tween(600))
            glowAlpha.animateTo(0.7f, tween(400))
        }

        delay(500)
        phase = "result"
        sound.play(if (result.won) Sfx.WIN else Sfx.LOSE)
        onResultShown()
        delay(300)
        showBanner = true
    }

    GameHeader(title = "Coin Flip", casinoBalance = casinoBalance, onBack = { onReset(); onBack() })

    Spacer(modifier = Modifier.height(8.dp))

    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                // === FLIPPING / LANDING / RESULT ===
                phase == "flipping" || phase == "landing" || (phase == "result" && result != null) -> {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Canvas for particles, glow, shadow, shockwave
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Background effects canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        ) {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f + 20f // offset for coin center area

                            // Glow behind coin
                            val glowA = glowAlpha.value
                            if (glowA > 0f) {
                                val glowColor = if (phase == "result" || phase == "landing")
                                    resultGlowColor else GoldBright
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            glowColor.copy(alpha = glowA * 0.5f),
                                            glowColor.copy(alpha = glowA * 0.2f),
                                            Color.Transparent,
                                        ),
                                        center = Offset(centerX, centerY + coinY.value),
                                        radius = 120f,
                                    ),
                                    center = Offset(centerX, centerY + coinY.value),
                                    radius = 120f,
                                )
                            }

                            // Shadow on "ground"
                            val shadowW = 60f * shadowScale.value
                            val shadowH = 12f * shadowScale.value
                            val shadowAlpha = (0.3f * shadowScale.value).coerceIn(0f, 0.4f)
                            drawOval(
                                color = Color.Black.copy(alpha = shadowAlpha),
                                topLeft = Offset(centerX - shadowW, centerY + 65f),
                                size = androidx.compose.ui.geometry.Size(shadowW * 2f, shadowH * 2f),
                            )

                            // Shockwave ring
                            val swAlpha = shockwaveAlpha.value
                            if (swAlpha > 0f) {
                                drawCircle(
                                    color = GoldBright.copy(alpha = swAlpha),
                                    center = Offset(centerX, centerY),
                                    radius = shockwaveRadius.value,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                                )
                            }

                            // Trail sparkles
                            trailParticles.forEach { p ->
                                val alpha = (p.life * 0.8f).coerceIn(0f, 1f)
                                drawCircle(
                                    color = p.color.copy(alpha = alpha),
                                    center = Offset(centerX + p.x, centerY + p.y + coinY.value),
                                    radius = p.size * p.life,
                                )
                            }

                            // Explosion particles
                            particles.forEach { p ->
                                val alpha = (p.life * 0.9f).coerceIn(0f, 1f)
                                drawCircle(
                                    color = p.color.copy(alpha = alpha),
                                    center = Offset(centerX + p.x, centerY + p.y),
                                    radius = p.size * p.life,
                                )
                            }
                        }

                        // Coin image
                        val currentRotation = coinRotation.value % 360f
                        val showHeads = currentRotation < 90f || currentRotation > 270f
                        val coinSide = if (phase == "result") result!!.result
                        else if (showHeads) "heads" else "tails"

                        // Horizontal squeeze for 3D perspective
                        val scaleX = abs(cos(coinRotation.value * PI.toFloat() / 180f))
                            .coerceAtLeast(0.05f)

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = coinY.value
                                    this.scaleX = coinScale.value * scaleX
                                    this.scaleY = coinScale.value
                                    cameraDistance = 16f * density
                                },
                        ) {
                            CoinFace(side = coinSide, size = 110)
                        }
                    }

                    // Status text
                    when (phase) {
                        "flipping" -> {
                            Text(
                                "Flipping...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldBright,
                            )
                        }
                        "landing" -> {
                            Text(
                                "Landing...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldBright,
                            )
                        }
                        "result" -> {
                            val resultColor = if (result!!.won) StatusConnected else StatusError
                            Text(
                                result.result.replaceFirstChar { it.uppercase() },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = resultColor,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // === LOADING ===
                loading -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Accent, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Placing bet...", fontSize = 13.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // === BETTING UI ===
                else -> {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Choice buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    ) {
                        ChoiceButton("Heads", COIN_HEADS_URL, choice == "heads") {
                            sound.play(Sfx.BUTTON)
                            choice = "heads"
                        }
                        ChoiceButton("Tails", COIN_TAILS_URL, choice == "tails") {
                            sound.play(Sfx.BUTTON)
                            choice = "tails"
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    BetInput(amount = amount, onAmountChange = { amount = it }, balance = casinoBalance, maxBet = 50_000)

                    if (error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error, fontSize = 11.sp, color = StatusError)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Pick a side and double your bet!  Payout: x2",
                        fontSize = 11.sp, color = TextMuted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val parsedAmount = amount.toLongOrNull()
                    val canPlay = parsedAmount != null && parsedAmount > 0 && choice != null
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canPlay) Accent else TextMuted.copy(alpha = 0.3f))
                            .clickable(enabled = canPlay) {
                                if (parsedAmount != null && choice != null) onPlay(parsedAmount, choice!!)
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Flip!", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (canPlay) SurfaceDark else TextMuted,
                        )
                    }
                }
            }
        }
    }

    // Result popup overlay
    if (result != null) {
        ResultPopup(
            visible = showBanner,
            won = result.won,
            title = if (result.won) "You Won!" else "You Lost",
            subtitle = result.result.replaceFirstChar { it.uppercase() },
            bet = result.bet,
            payout = result.payout,
            onReplay = {
                val lastBet = result.bet
                val lastChoice = choice
                phase = "idle"; showBanner = false; onReset()
                if (lastChoice != null) onPlay(lastBet, lastChoice)
            },
            onBack = { phase = "idle"; showBanner = false; onReset() },
        )
    }
}

// ── Coin face image ──

@Composable
private fun CoinFace(side: String, size: Int) {
    val url = if (side == "heads") COIN_HEADS_URL else COIN_TAILS_URL
    AsyncImage(
        model = url, contentDescription = side,
        modifier = Modifier.size(size.dp).clip(CircleShape),
    )
}

// ── Choice button with shimmer on selected ──

@Composable
private fun ChoiceButton(label: String, imageUrl: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "choiceScale",
    )

    // Shimmer animation on selected
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "shimmerOffset",
    )

    val borderColor = if (selected) GoldBright else SurfaceBorder
    val bgColor = if (selected) GoldBright.copy(alpha = 0.1f) else Color.Transparent

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp)),
    ) {
        Column(
            modifier = Modifier
                .background(bgColor)
                .border(2.dp, borderColor, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Coin image with subtle glow when selected
            Box(contentAlignment = Alignment.Center) {
                if (selected) {
                    // Glow behind coin
                    Canvas(modifier = Modifier.size(56.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    GoldBright.copy(alpha = 0.3f),
                                    Color.Transparent,
                                ),
                            ),
                            radius = 32f,
                        )
                    }
                }
                AsyncImage(
                    model = imageUrl, contentDescription = label,
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                label, fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) GoldBright else TextPrimary,
            )
        }

        // Shimmer overlay when selected
        if (selected) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                val shimmerWidth = size.width * 0.4f
                val startX = shimmerOffset * size.width
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                        startX = startX,
                        endX = startX + shimmerWidth,
                    ),
                )
            }
        }
    }
}
