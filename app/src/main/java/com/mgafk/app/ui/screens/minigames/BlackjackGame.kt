package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.data.repository.BlackjackCard
import com.mgafk.app.ui.BlackjackUiState
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

private val SUIT_SYMBOLS = mapOf(
    "hearts" to "\u2665",
    "diamonds" to "\u2666",
    "clubs" to "\u2663",
    "spades" to "\u2660",
)

private val SUIT_COLORS = mapOf(
    "hearts" to Color(0xFFE74C3C),
    "diamonds" to Color(0xFFE74C3C),
    "clubs" to Color(0xFF2C3E50),
    "spades" to Color(0xFF2C3E50),
)

@Composable
fun BlackjackGame(
    casinoBalance: Long?,
    state: BlackjackUiState,
    onStart: (amount: Long) -> Unit,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onDouble: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    val sound = rememberSoundManager()
    var amount by remember { mutableStateOf("") }
    // Track card count to animate only new cards
    var prevPlayerCount by remember { mutableIntStateOf(0) }
    var prevDealerCount by remember { mutableIntStateOf(0) }
    var showResultBanner by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    val resp = state.response
    val isDone = resp?.status == "done"
    val isPlaying = resp?.status == "playing"

    // Animate dealing sequence when game first starts or new card arrives
    LaunchedEffect(resp?.player?.cards?.size, resp?.dealer?.cards?.size) {
        if (resp != null && state.active) {
            val playerCount = resp.player.cards.size
            val dealerCount = resp.dealer.cards.size
            // On initial deal (2+2), stagger them
            if (prevPlayerCount == 0 && playerCount >= 2) {
                showActions = false
                sound.play(Sfx.CARD_DEAL)
                delay(800) // wait for dealing animation
                showActions = true
            } else if (playerCount > prevPlayerCount || dealerCount > prevDealerCount) {
                // New card drawn
                showActions = false
                sound.play(Sfx.CARD_FLIP)
                delay(400)
                showActions = true
            }
            prevPlayerCount = playerCount
            prevDealerCount = dealerCount
        }
    }

    // Show result banner with delay after game ends
    LaunchedEffect(isDone) {
        if (isDone) {
            showActions = false
            sound.play(Sfx.CARD_FLIP)
            delay(600) // let dealer card flip animation play
            val won = resp?.result in listOf("blackjack", "win", "dealer_bust")
            val push = resp?.result == "push"
            if (won) sound.play(if (resp?.result == "blackjack") Sfx.BIG_WIN else Sfx.WIN)
            else if (!push) sound.play(Sfx.LOSE)
            showResultBanner = true
        } else {
            showResultBanner = false
        }
    }

    // Reset counters when game resets
    LaunchedEffect(state.active) {
        if (!state.active) {
            prevPlayerCount = 0
            prevDealerCount = 0
            showResultBanner = false
            showActions = false
        }
    }

    GameHeader(title = "Blackjack", casinoBalance = casinoBalance, onBack = { onReset(); onBack() })

    Spacer(modifier = Modifier.height(8.dp))

    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                // Active game
                state.active && resp != null -> {
                    // Dealer hand
                    AnimatedHandSection(
                        label = "Dealer",
                        cards = resp.dealer.cards,
                        value = if (isDone) resp.dealer.value else resp.dealer.visibleValue,
                        isHidden = !isDone,
                        blackjack = resp.dealer.blackjack,
                        revealHidden = isDone,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // VS divider with glow
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(SurfaceBorder),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                        ) {
                            Text("VS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Player hand
                    AnimatedHandSection(
                        label = "You",
                        cards = resp.player.cards,
                        value = resp.player.value,
                        blackjack = resp.player.blackjack,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isDone) {
                        // popup handles result
                    } else if (isPlaying) {
                        // Action buttons with slide-in
                        AnimatedVisibility(
                            visible = showActions && !state.loading,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            ) + fadeIn(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ActionButton("Hit", Accent, Modifier.weight(1f), onHit)
                                ActionButton("Stand", StatusError, Modifier.weight(1f), onStand)
                                if (resp.canDouble) {
                                    ActionButton("Double", StatusConnected, Modifier.weight(1f), onDouble)
                                }
                            }
                        }

                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Accent, strokeWidth = 3.dp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bet: ${numberFormat.format(resp.bet)}", fontSize = 12.sp, color = TextMuted)
                        }
                    }

                    if (state.error != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.error, fontSize = 11.sp, color = StatusError)
                    }
                }

                // Loading
                state.loading -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Accent, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Dealing cards...", fontSize = 13.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Betting UI
                else -> {
                    Spacer(modifier = Modifier.height(8.dp))

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
                                "Classic Blackjack - Beat the dealer!",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = TextPrimary, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                PayoutChip("Blackjack", "x2.5")
                                PayoutChip("Win", "x2")
                                PayoutChip("Push", "x1")
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
                            "Deal!", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (canPlay) SurfaceDark else TextMuted,
                        )
                    }
                }
            }
        }
    }

    if (isDone && resp != null) {
        val resultInfo = getResultInfo(resp.result)
        val bjWon = resp.result in listOf("blackjack", "win", "dealer_bust")
        ResultPopup(
            visible = showResultBanner,
            won = bjWon,
            title = resultInfo.title,
            subtitle = resultInfo.subtitle,
            bet = resp.bet,
            payout = resp.payout,
            onReplay = {
                val lastBet = resp.bet
                onReset()
                onStart(lastBet)
            },
            onBack = { onReset() },
        )
    }
}

// ── Animated hand section ──

@Composable
private fun AnimatedHandSection(
    label: String,
    cards: List<BlackjackCard>,
    value: Int,
    isHidden: Boolean = false,
    blackjack: Boolean = false,
    revealHidden: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(modifier = Modifier.width(8.dp))
            if (blackjack) {
                // Golden pulse for blackjack
                val pulseScale = remember { Animatable(1f) }
                LaunchedEffect(Unit) {
                    while (true) {
                        pulseScale.animateTo(1.15f, tween(600))
                        pulseScale.animateTo(1f, tween(600))
                    }
                }
                Text(
                    "BLACKJACK!",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier.graphicsLayer { scaleX = pulseScale.value; scaleY = pulseScale.value },
                )
            } else {
                Text(
                    "$value", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, color = TextPrimary,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy((-16).dp, Alignment.CenterHorizontally),
        ) {
            cards.forEachIndexed { index, card ->
                val isHiddenCard = card.rank == "?"
                key("${label}_${index}_${card.rank}_${card.suit}") {
                    AnimatedCard(
                        card = card,
                        isHiddenCard = isHiddenCard,
                        revealHidden = revealHidden && isHiddenCard,
                        dealDelay = index * 150L,
                    )
                }
            }
        }
    }
}

// ── Animated card with flip + slide-in ──

@Composable
private fun AnimatedCard(
    card: BlackjackCard,
    isHiddenCard: Boolean,
    revealHidden: Boolean,
    dealDelay: Long,
) {
    // Slide in from right
    val slideOffset = remember { Animatable(200f) }
    // Flip rotation: 0 = face up, 180 = face down
    val flipRotation = remember { Animatable(180f) }
    // Scale bounce on arrive
    val scale = remember { Animatable(0.3f) }

    LaunchedEffect(Unit) {
        delay(dealDelay)
        // Slide in + scale up in parallel
        launch { slideOffset.animateTo(0f, tween(400, easing = FastOutSlowInEasing)) }
        launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        // Flip to face (or stay face-down if hidden)
        delay(100)
        if (!isHiddenCard) {
            flipRotation.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
        } else {
            flipRotation.animateTo(180f, tween(10))
        }
    }

    // Reveal hidden card (dealer reveal)
    LaunchedEffect(revealHidden) {
        if (revealHidden) {
            flipRotation.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        }
    }

    val showFace = flipRotation.value < 90f
    val actualCard = if (revealHidden || !isHiddenCard) card else null

    Box(
        modifier = Modifier
            .offset { IntOffset(slideOffset.value.toInt(), 0) }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationY = flipRotation.value
                cameraDistance = 12f * density
            },
    ) {
        if (showFace && actualCard != null && actualCard.rank != "?") {
            CardFace(card = actualCard)
        } else {
            CardBack()
        }
    }
}

// ── Card rendering ──

@Composable
private fun CardFace(card: BlackjackCard) {
    val suitSymbol = SUIT_SYMBOLS[card.suit] ?: card.suit
    val suitColor = SUIT_COLORS[card.suit] ?: TextPrimary

    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 90.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.5.dp, Color(0xFFDDDDDD), RoundedCornerShape(10.dp))
            .padding(5.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(card.rank, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = suitColor, lineHeight = 17.sp)
            Text(suitSymbol, fontSize = 11.sp, color = suitColor, lineHeight = 13.sp)
        }
        Text(suitSymbol, fontSize = 26.sp, color = suitColor, modifier = Modifier.align(Alignment.Center))
        // Bottom-right mirrored
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .graphicsLayer { rotationZ = 180f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(card.rank, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = suitColor, lineHeight = 14.sp)
            Text(suitSymbol, fontSize = 9.sp, color = suitColor, lineHeight = 11.sp)
        }
    }
}

@Composable
private fun CardBack() {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 90.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Accent)
            .border(1.5.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Diamond pattern
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 74.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(1.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "\u2666", fontSize = 22.sp,
                color = Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

// ── Action button ──

@Composable
private fun ActionButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Subtle scale on press
    var pressed by remember { mutableStateOf(false) }
    val btnScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "btnScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable {
                pressed = true
                onClick()
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SurfaceDark)
    }

    // Reset press state
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(150)
            pressed = false
        }
    }
}

@Composable
private fun PayoutChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 10.sp, color = TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
    }
}

private data class ResultInfo(val title: String, val subtitle: String, val color: Color)

private fun getResultInfo(result: String?): ResultInfo = when (result) {
    "blackjack" -> ResultInfo("Blackjack!", "Natural 21 - x2.5 payout", Color(0xFFFFD700))
    "win" -> ResultInfo("You Won!", "Beat the dealer", StatusConnected)
    "dealer_bust" -> ResultInfo("Dealer Bust!", "Dealer went over 21", StatusConnected)
    "push" -> ResultInfo("Push", "Tie - bet refunded", Accent)
    "bust" -> ResultInfo("Bust!", "You went over 21", StatusError)
    "lose" -> ResultInfo("You Lost", "Dealer wins", StatusError)
    "dealer_blackjack" -> ResultInfo("Dealer Blackjack", "Dealer had a natural 21", StatusError)
    else -> ResultInfo("Game Over", "", TextMuted)
}
