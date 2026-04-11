package com.mgafk.app.ui.screens.minigames

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mgafk.app.R
import com.mgafk.app.data.model.PlayerSnapshot
import com.mgafk.app.ui.CrashUiState
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

@Composable
fun CrashGame(
    casinoBalance: Long?,
    state: CrashUiState,
    playerSnapshot: PlayerSnapshot?,
    gameVersion: String,
    gameHost: String,
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
    val noiseHistory = remember { mutableListOf<Pair<Long, Double>>() }

    // Milestone tracking
    var lastMilestone by remember { mutableIntStateOf(0) }
    val milestoneScale = remember { Animatable(1f) }
    var milestoneText by remember { mutableStateOf("") }
    var showMilestone by remember { mutableStateOf(false) }

    // Danger alert state
    var alarmPlaying by remember { mutableStateOf(false) }

    // Particles for trail + explosion
    val particles = remember { mutableStateListOf<CrashParticle>() }


    // Stars for background (generated once)
    val stars = remember {
        List(60) {
            Star(
                xFrac = Math.random().toFloat(),
                yFrac = Math.random().toFloat(),
                size = 1f + Math.random().toFloat() * 2.5f,
                twinkleSpeed = 0.5f + Math.random().toFloat() * 2f,
                twinkleOffset = Math.random().toFloat() * 100f,
            )
        }
    }

    // Rocket position (0 = bottom, 1 = top of scene)
    var rocketProgress by remember { mutableStateOf(0f) }
    // When true, rocket is replaced by explosion
    var rocketExploded by remember { mutableStateOf(false) }
    // Rocket tilt for turbulence
    var rocketTilt by remember { mutableStateOf(0f) }
    // Animated star scroll offset
    var starScroll by remember { mutableStateOf(0f) }

    // Shake on crash
    val crashShakeX = remember { Animatable(0f) }
    val crashShakeY = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }
    val multScale = remember { Animatable(1f) }

    // Rocket shake intensity (increases with multiplier)
    val rocketShakeIntensity by animateFloatAsState(
        targetValue = when {
            !state.active || state.crashed || state.cashedOut -> 0f
            localMultiplier >= 10.0 -> 6f
            localMultiplier >= 5.0 -> 4f
            localMultiplier >= 2.0 -> 2f
            else -> 0.5f
        },
        animationSpec = tween(800),
        label = "rocketShake",
    )

    // Animate multiplier + particles + rocket position
    LaunchedEffect(state.active, state.crashed, state.cashedOut) {
        if (state.active && !state.crashed && !state.cashedOut && state.startTime > 0) {
            showBanner = false
            lastMilestone = 0
            showMilestone = false
            alarmPlaying = false
            sound.play(Sfx.CRASH_RISING, 0.5f, loop = true)
            noiseHistory.clear()
            particles.clear()
            rocketProgress = 0f
            rocketExploded = false
            starScroll = 0f
            var currentNoise = 0.0
            var noiseTarget = 0.0
            var frameCount = 0
            while (true) {
                val now = System.currentTimeMillis()
                elapsedMs = now - state.startTime
                val realMult = exp(state.growthRate * elapsedMs)
                localMultiplier = realMult

                // Rocket progress: log scale normalized for visual
                val logMult = ln(realMult.coerceAtLeast(1.0))
                rocketProgress = (logMult / 4.0).toFloat().coerceIn(0f, 0.85f)

                // Star scroll speed increases with multiplier
                starScroll += (0.002f + rocketProgress * 0.015f)

                // Rocket tilt from noise
                rocketTilt = (Math.random().toFloat() * 2f - 1f) * rocketShakeIntensity

                // Turbulence
                if (frameCount % 15 == 0) {
                    val amplitude = (realMult * 0.04).coerceIn(0.01, 0.8)
                    noiseTarget = (Math.random() * 2 - 1) * amplitude
                    if (Math.random() < 0.10 && realMult > 1.3) {
                        noiseTarget = -amplitude * 2.5
                    }
                }
                currentNoise += (noiseTarget - currentNoise) * 0.12
                displayMultiplierNoise = currentNoise

                if (frameCount % 5 == 0) {
                    noiseHistory.add(elapsedMs to currentNoise)
                    if (noiseHistory.size > 500) noiseHistory.removeAt(0)
                }

                // Update particles
                val iter = particles.listIterator()
                while (iter.hasNext()) {
                    val p = iter.next()
                    p.x += p.vx
                    p.y += p.vy
                    p.life -= p.decay
                    if (p.life <= 0f) iter.remove()
                }
                while (particles.size > 100) particles.removeAt(0)

                // Milestone check
                val milestone = when {
                    realMult >= 20.0 -> 20
                    realMult >= 10.0 -> 10
                    realMult >= 5.0 -> 5
                    realMult >= 2.0 -> 2
                    else -> 0
                }
                if (milestone > lastMilestone && milestone > 0) {
                    lastMilestone = milestone
                    milestoneText = "${milestone}x"
                    showMilestone = true
                    sound.play(Sfx.STREAK, 0.6f)
                    launch {
                        milestoneScale.snapTo(0.5f)
                        milestoneScale.animateTo(
                            1.2f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        )
                        milestoneScale.animateTo(1f, tween(200))
                        delay(800)
                        showMilestone = false
                    }
                }

                // Start alarm at 5x
                if (realMult >= 5.0 && !alarmPlaying) {
                    alarmPlaying = true
                    sound.play(Sfx.ALARM, 0.4f, loop = true)
                }

                frameCount++
                delay(33)
            }
        }
    }

    val gameOver = state.crashed || state.cashedOut

    // Game over effect
    LaunchedEffect(gameOver) {
        if (!gameOver) return@LaunchedEffect
        displayMultiplierNoise = 0.0
        showBanner = false
        showMilestone = false
        sound.stop(Sfx.CRASH_RISING)
        if (alarmPlaying) {
            sound.stop(Sfx.ALARM)
            alarmPlaying = false
        }

        if (state.crashed) {
            localMultiplier = state.crashPoint
            rocketTilt = 0f
            rocketExploded = true

            // Big explosion — lots of particles in all directions
            repeat(80) {
                val angle = Math.random() * 2 * PI
                val speed = 1f + Math.random().toFloat() * 12f
                particles.add(
                    CrashParticle(
                        x = (Math.random() * 10 - 5).toFloat(),
                        y = (Math.random() * 10 - 5).toFloat(),
                        vx = (cos(angle) * speed).toFloat(),
                        vy = (sin(angle) * speed).toFloat(),
                        life = 1f,
                        decay = 0.008f + Math.random().toFloat() * 0.015f,
                        size = 2f + Math.random().toFloat() * 8f,
                        color = listOf(
                            StatusError,
                            Color(0xFFFF6B00),
                            Color(0xFFFFD700),
                            Color(0xFFFF4400),
                            Color.White,
                        ).random(),
                    ),
                )
            }
            // Debris chunks (bigger, slower)
            repeat(15) {
                val angle = Math.random() * 2 * PI
                val speed = 2f + Math.random().toFloat() * 5f
                particles.add(
                    CrashParticle(
                        x = 0f, y = 0f,
                        vx = (cos(angle) * speed).toFloat(),
                        vy = (sin(angle) * speed).toFloat(),
                        life = 1f,
                        decay = 0.006f + Math.random().toFloat() * 0.008f,
                        size = 6f + Math.random().toFloat() * 10f,
                        color = listOf(
                            Color(0xFF888888),
                            Color(0xFFAAAAAA),
                            Color(0xFF6C8CFF).copy(alpha = 0.7f),
                        ).random(),
                    ),
                )
            }

            sound.play(Sfx.LOSE)
            launch {
                flashAlpha.animateTo(0.4f, tween(50))
                flashAlpha.animateTo(0f, tween(600))
            }
            repeat(10) {
                val intensity = (10 - it) * 6f
                crashShakeX.animateTo((-intensity..intensity).random().toFloat(), tween(30))
                crashShakeY.animateTo((-intensity * 0.6f..intensity * 0.6f).random().toFloat(), tween(30))
            }
            crashShakeX.animateTo(0f, tween(60))
            crashShakeY.animateTo(0f, tween(60))
            delay(400)
        } else {
            localMultiplier = state.multiplier
            sound.play(Sfx.CASHOUT)
            rocketTilt = 0f

            // Green sparkles
            repeat(20) {
                val angle = Math.random() * 2 * PI
                val speed = 1.5f + Math.random().toFloat() * 5f
                particles.add(
                    CrashParticle(
                        x = 0f, y = 0f,
                        vx = (cos(angle) * speed).toFloat(),
                        vy = (sin(angle) * speed).toFloat(),
                        life = 1f,
                        decay = 0.018f + Math.random().toFloat() * 0.02f,
                        size = 2f + Math.random().toFloat() * 4f,
                        color = listOf(StatusConnected, Color(0xFF90EE90), Color(0xFFFFD700), Color.White).random(),
                    ),
                )
            }

            multScale.animateTo(1.5f, tween(120))
            multScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            delay(300)
        }

        // Fade out particles
        launch {
            repeat(40) {
                val iter = particles.listIterator()
                while (iter.hasNext()) {
                    val p = iter.next()
                    p.x += p.vx
                    p.y += p.vy
                    p.vx *= 0.94f
                    p.vy *= 0.94f
                    p.life -= p.decay
                    if (p.life <= 0f) iter.remove()
                }
                delay(33)
            }
            particles.clear()
        }

        showBanner = true
    }

    // Cashout pulse — accelerates with multiplier
    val pulseDuration = when {
        localMultiplier >= 10.0 -> 180
        localMultiplier >= 5.0 -> 280
        localMultiplier >= 2.0 -> 400
        else -> 600
    }
    val infiniteTransition = rememberInfiniteTransition(label = "cashoutPulse")
    val cashoutPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cashoutScale",
    )

    // Danger border glow
    val dangerAlpha by animateFloatAsState(
        targetValue = when {
            !state.active || gameOver -> 0f
            localMultiplier >= 10.0 -> 0.7f
            localMultiplier >= 5.0 -> 0.4f
            localMultiplier >= 2.0 -> 0.15f
            else -> 0f
        },
        animationSpec = tween(800),
        label = "dangerAlpha",
    )
    val dangerColor by animateColorAsState(
        targetValue = when {
            localMultiplier >= 10.0 -> StatusError
            localMultiplier >= 5.0 -> Color(0xFFFFA500)
            else -> Color(0xFFFFD700)
        },
        animationSpec = tween(800),
        label = "dangerColor",
    )

    // Danger flash (pulsing red overlay when >= 5x)
    val dangerFlash by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dangerFlash",
    )

    // Build avatar layer URLs
    val avatarLayers = remember(playerSnapshot, gameVersion, gameHost) {
        if (playerSnapshot == null) return@remember emptyList<String>()
        val host = gameHost.removePrefix("https://").removePrefix("http://").ifBlank { "magicgarden.gg" }
        val baseUrl = "https://$host/version/$gameVersion/assets/cosmetic"
        listOf(
            playerSnapshot.avatarBottom,
            playerSnapshot.avatarMid,
            playerSnapshot.avatarTop,
            playerSnapshot.avatarExpression,
        ).filter { it.isNotBlank() }.map { "$baseUrl/$it" }
    }

    GameHeader(title = "Crash", casinoBalance = casinoBalance, onBack = { onReset(); onBack() })

    Spacer(modifier = Modifier.height(8.dp))

    // Main card
    Box {
        AppCard(
            modifier = Modifier
                .graphicsLayer {
                    translationX = crashShakeX.value
                    translationY = crashShakeY.value
                }
                .then(
                    if (dangerAlpha > 0f) {
                        Modifier.border(
                            1.5.dp,
                            dangerColor.copy(alpha = dangerAlpha * 0.5f),
                            RoundedCornerShape(16.dp),
                        )
                    } else Modifier,
                ),
        ) {
            val density = LocalDensity.current
            val context = LocalContext.current

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    state.active -> {
                        val displayMultiplier = if (gameOver) {
                            if (state.crashed) state.crashPoint else state.multiplier
                        } else localMultiplier.coerceAtLeast(1.0)

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

                        val fontSize by animateFloatAsState(
                            targetValue = when {
                                displayMultiplier >= 10.0 -> 58f
                                displayMultiplier >= 5.0 -> 54f
                                displayMultiplier >= 2.0 -> 50f
                                else -> 46f
                            },
                            animationSpec = tween(500),
                            label = "fontSize",
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // ── ROCKET SCENE ──
                        val currentTheme = themeForMultiplier(displayMultiplier)

                        // Ground scroll: 0 = fully visible, 1+ = off screen
                        val groundScroll = (starScroll * 10f).coerceIn(0f, 3f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        ) {
                            // Background canvas (sky + stars + particles)
                            Canvas(
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                // Sky: stays #052740 while ground is visible, then transitions to space
                                val imageTopColor = Color(0xFF052740)
                                // groundScroll hits ~1.7 when image is fully off screen
                                val skyProgress = ((groundScroll - 1.7f) / 1f).coerceIn(0f, 1f)
                                val skyTopColor = lerp(imageTopColor, currentTheme.topColor, skyProgress)
                                val skyBottomColor = lerp(imageTopColor, currentTheme.bottomColor, skyProgress)
                                drawRect(
                                    Brush.verticalGradient(
                                        colors = listOf(skyTopColor, skyBottomColor),
                                    ),
                                )

                                // Scrolling stars (fade in only after ground is gone)
                                val starsVisibility = ((groundScroll - 1.5f) / 0.5f).coerceIn(0f, 1f)
                                val starAlphaBase = currentTheme.starColor.alpha * starsVisibility
                                for (star in stars) {
                                    val sy = ((star.yFrac + starScroll * star.twinkleSpeed) % 1.2f)
                                    if (sy < 0f || sy > 1f) continue
                                    val twinkle = (sin((starScroll * star.twinkleSpeed * 10 + star.twinkleOffset).toDouble()) * 0.5 + 0.5).toFloat()
                                    drawCircle(
                                        color = currentTheme.starColor.copy(alpha = starAlphaBase * twinkle),
                                        radius = star.size,
                                        center = Offset(star.xFrac * size.width, sy * size.height),
                                    )
                                }

                                // Danger zone vignette (red edges when >= 5x)
                                if (displayMultiplier >= 5.0) {
                                    val vignetteAlpha = if (displayMultiplier >= 10.0) {
                                        0.15f + dangerFlash * 0.1f
                                    } else {
                                        0.08f + dangerFlash * 0.05f
                                    }
                                    // Left edge
                                    drawRect(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                StatusError.copy(alpha = vignetteAlpha),
                                                Color.Transparent,
                                            ),
                                            startX = 0f,
                                            endX = size.width * 0.3f,
                                        ),
                                    )
                                    // Right edge
                                    drawRect(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                StatusError.copy(alpha = vignetteAlpha),
                                            ),
                                            startX = size.width * 0.7f,
                                            endX = size.width,
                                        ),
                                    )
                                    // Top edge
                                    drawRect(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                StatusError.copy(alpha = vignetteAlpha * 0.7f),
                                                Color.Transparent,
                                            ),
                                            startY = 0f,
                                            endY = size.height * 0.25f,
                                        ),
                                    )
                                }

                            }

                            // Ground image scrolling down (between sky and rocket)
                            if (groundScroll < 2f) {
                                Image(
                                    painter = painterResource(R.drawable.bg_crash_ground),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(380.dp)
                                        .align(Alignment.BottomCenter)
                                        .graphicsLayer {
                                            translationY = groundScroll * 380.dp.toPx() * 0.6f
                                        },
                                )
                            }

                            // Rocket + particles + flame (on top of ground)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = size.width / 2f
                                val rocketY = size.height * (1f - rocketProgress) * 0.7f + size.height * 0.15f

                                // Particles
                                for (p in particles) {
                                    drawCircle(
                                        color = p.color.copy(alpha = (p.life * 0.8f).coerceIn(0f, 1f)),
                                        radius = p.size * p.life,
                                        center = Offset(cx + p.x, rocketY + p.y),
                                    )
                                }

                                if (!rocketExploded) {
                                    // Flame below rocket
                                    if (!gameOver) {
                                        val flameTopY = rocketY + 87f
                                        val flameHeight = 30f + rocketProgress * 50f
                                        val flameFlicker = (Math.random() * 8 - 4).toFloat()
                                        drawRocketFlame(
                                            cx = cx,
                                            topY = flameTopY,
                                            flameHeight = flameHeight + flameFlicker,
                                            multiplier = displayMultiplier,
                                        )
                                    }

                                    // Rocket
                                    drawRocket(
                                        cx = cx,
                                        cy = rocketY,
                                        tilt = rocketTilt,
                                        cashedOut = state.cashedOut,
                                        multiplier = displayMultiplier,
                                    )
                                } else {
                                    // Explosion fireball glow where the rocket was
                                    drawCircle(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFFFFDD00).copy(alpha = 0.6f),
                                                Color(0xFFFF6B00).copy(alpha = 0.3f),
                                                Color.Transparent,
                                            ),
                                        ),
                                        radius = 60f,
                                        center = Offset(cx, rocketY),
                                    )
                                }
                            }

                            // Avatar in porthole (hidden when rocket exploded)
                            if (avatarLayers.isNotEmpty() && !rocketExploded) {
                                val avatarSize = 16.dp
                                val rocketCenterYFrac = (1f - rocketProgress) * 0.7f + 0.15f
                                val sceneHeightPx = with(density) { 380.dp.toPx() }
                                // Porthole offset: bodyTop + 35 relative to rocketY
                                // bodyTop = cy - 60 + 17.5 = cy - 42.5
                                // portholeY = cy - 42.5 + 35 = cy - 7.5
                                val portholeOffsetFrac = -7.5f / sceneHeightPx

                                val avatarSizePx = with(density) { avatarSize.toPx() }

                                Box(
                                    modifier = Modifier
                                        .size(avatarSize)
                                        .align(Alignment.TopCenter)
                                        .offset {
                                            IntOffset(
                                                x = 0,
                                                y = (sceneHeightPx * (rocketCenterYFrac + portholeOffsetFrac) - avatarSizePx / 2f).toInt(),
                                            )
                                        }
                                        .graphicsLayer {
                                            rotationZ = rocketTilt * 0.5f
                                        }
                                        .clip(CircleShape)
                                        .background(Color(0xFF1A2940)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    avatarLayers.forEach { url ->
                                        val model = remember(url) {
                                            ImageRequest.Builder(context)
                                                .data(url)
                                                .crossfade(true)
                                                .build()
                                        }
                                        AsyncImage(
                                            model = model,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(avatarSize)
                                                .graphicsLayer {
                                                    scaleX = 1.8f
                                                    scaleY = 1.8f
                                                    translationY = avatarSize.toPx() * 0.25f
                                                },
                                        )
                                    }
                                }
                            }

                            // Milestone popup overlay
                            if (showMilestone) {
                                Text(
                                    milestoneText,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFFFD700),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .graphicsLayer {
                                            scaleX = milestoneScale.value
                                            scaleY = milestoneScale.value
                                            alpha = milestoneScale.value.coerceIn(0f, 1f)
                                        },
                                )
                            }

                            // Zone label (top right)
                            if (state.active && !gameOver) {
                                Text(
                                    currentTheme.label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (displayMultiplier >= 5.0) {
                                        StatusError.copy(alpha = 0.5f + dangerFlash * 0.3f)
                                    } else {
                                        Color.White.copy(alpha = 0.3f)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                )
                            }

                            // CRASHED / CASHED OUT overlay on scene
                            if (state.crashed) {
                                Text(
                                    "CRASHED!",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = StatusError,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            } else if (state.cashedOut) {
                                Text(
                                    "CASHED OUT!",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = StatusConnected,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Big multiplier with glow
                        Box(contentAlignment = Alignment.Center) {
                            if (!gameOver && displayMultiplier >= 2.0) {
                                Text(
                                    "${"%.2f".format(displayMultiplier)}x",
                                    fontSize = (fontSize + 4).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = multiplierColor.copy(alpha = 0.12f),
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = multScale.value * 1.1f
                                        scaleY = multScale.value * 1.1f
                                    },
                                )
                            }
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
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Bet & live profit
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bet: ${numberFormat.format(state.bet)}", fontSize = 13.sp, color = TextMuted)
                            if (!gameOver) {
                                Spacer(modifier = Modifier.width(12.dp))
                                val profit = ((state.bet * displayMultiplier) - state.bet).toLong()
                                val profitColor by animateColorAsState(
                                    targetValue = when {
                                        profit >= state.bet * 5 -> Color(0xFFFFD700)
                                        profit >= state.bet -> StatusConnected
                                        else -> StatusConnected.copy(alpha = 0.7f)
                                    },
                                    animationSpec = tween(300),
                                    label = "profitColor",
                                )
                                Text(
                                    "+${numberFormat.format(profit)}",
                                    fontSize = 13.sp, color = profitColor, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (gameOver) {
                            // popup handles result
                        } else {
                            // Cashout button with urgency
                            val cashoutAmount = (state.bet * localMultiplier).toLong()
                            val cashoutBgColor by animateColorAsState(
                                targetValue = when {
                                    localMultiplier >= 10.0 -> Color(0xFFFF4444)
                                    localMultiplier >= 5.0 -> Color(0xFFFFA500)
                                    localMultiplier >= 2.0 -> Color(0xFFFFD700)
                                    else -> StatusConnected
                                },
                                animationSpec = tween(500),
                                label = "cashoutBg",
                            )
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
                                                cashoutBgColor,
                                                cashoutBgColor.copy(alpha = 0.85f),
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

                        // Preview of rocket on the ground (idle state)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        ) {
                            // Garden background
                            Image(
                                painter = painterResource(R.drawable.bg_crash_ground),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Dim overlay so rocket is visible
                                drawRect(Color.Black.copy(alpha = 0.15f))

                                // A few stars barely visible (it's daytime)
                                for (star in stars.take(8)) {
                                    drawCircle(
                                        Color.White.copy(alpha = 0.15f),
                                        radius = star.size * 0.6f,
                                        center = Offset(star.xFrac * size.width, star.yFrac * size.height * 0.4f),
                                    )
                                }
                                // Idle rocket
                                drawRocket(
                                    cx = size.width / 2f,
                                    cy = size.height * 0.5f,
                                    tilt = 0f,
                                    cashedOut = false,
                                    multiplier = 1.0,
                                )
                            }

                            // Avatar in idle rocket
                            if (avatarLayers.isNotEmpty()) {
                                val idleAvatarSize = 16.dp
                                Box(
                                    modifier = Modifier
                                        .size(idleAvatarSize)
                                        .align(Alignment.Center)
                                        .offset(y = (-6).dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1A2940)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    avatarLayers.forEach { url ->
                                        val model = remember(url) {
                                            ImageRequest.Builder(context)
                                                .data(url)
                                                .crossfade(true)
                                                .build()
                                        }
                                        AsyncImage(
                                            model = model,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(idleAvatarSize)
                                                .graphicsLayer {
                                                    scaleX = 1.8f
                                                    scaleY = 1.8f
                                                    translationY = idleAvatarSize.toPx() * 0.25f
                                                },
                                        )
                                    }
                                }
                            }

                            Text(
                                "Ready for launch",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp),
                            )
                        }

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

                        BetInput(amount = amount, onAmountChange = { amount = it }, balance = casinoBalance)

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
                                "Launch!", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                color = if (canPlay) SurfaceDark else TextMuted,
                            )
                        }
                    }
                }
            }
        }

        // Red flash overlay
        if (flashAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(StatusError.copy(alpha = flashAlpha.value)),
            )
        }
    }

    if (gameOver && state.active) {
        ResultPopup(
            visible = showBanner,
            won = state.won,
            title = if (state.won) "Cashed Out!" else "CRASHED!",
            subtitle = if (state.crashed) "Crashed at ${"%.2f".format(state.crashPoint)}x" else "Cashed at ${"%.2f".format(state.multiplier)}x",
            bet = state.bet,
            payout = state.payout,
            onReplay = {
                val lastBet = state.bet
                onReset()
                onStart(lastBet)
            },
            onBack = { onReset() },
        )
    }
}

