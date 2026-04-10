package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.shape.CircleShape
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
import java.text.NumberFormat
import java.util.Locale

private val numberFormat = NumberFormat.getNumberInstance(Locale.US)
private const val COIN_HEADS_URL = "https://i.imgur.com/yPcQYDB.png"
private const val COIN_TAILS_URL = "https://i.imgur.com/J2gqn25.png"

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
    var amount by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf<String?>(null) }
    var animating by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }

    // When result arrives, start animation then show result
    LaunchedEffect(result) {
        if (result != null && !showResult) {
            animating = true
            delay(2000) // animation duration
            animating = false
            showResult = true
            onResultShown()
        }
    }

    // Header with back button
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            onReset()
            onBack()
        }) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Text(
            text = "Coin Flip",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.weight(1f))
        // Balance display
        if (casinoBalance != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = numberFormat.format(casinoBalance),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = StatusConnected,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // ── Game area ──
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                // ── Animating ──
                animating && result != null -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    CoinAnimation(targetResult = result.result)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Flipping...", fontSize = 14.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ── Show result ──
                showResult && result != null -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    CoinFace(side = result.result, size = 100)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = result.result.replaceFirstChar { it.uppercase() },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Win / Lose banner
                    val won = result.won
                    val bannerColor = if (won) StatusConnected else StatusError
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
                                text = if (won) "You Won!" else "You Lost",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = bannerColor,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (won) "+${numberFormat.format(result.payout)}" else "-${numberFormat.format(result.bet)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = bannerColor,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Play again button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Accent)
                            .clickable {
                                showResult = false
                                onReset()
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Play Again", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SurfaceDark)
                    }
                }

                // ── Loading (waiting for server) ──
                loading -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Accent, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Placing bet...", fontSize = 13.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // ── Betting UI ──
                else -> {
                    // Coin preview
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    ) {
                        ChoiceButton(
                            label = "Heads",
                            imageUrl = COIN_HEADS_URL,
                            selected = choice == "heads",
                            onClick = { choice = "heads" },
                        )
                        ChoiceButton(
                            label = "Tails",
                            imageUrl = COIN_TAILS_URL,
                            selected = choice == "tails",
                            onClick = { choice = "tails" },
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Amount input
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { new -> amount = new.filter { it.isDigit() } },
                        label = { Text("Bet amount") },
                        placeholder = { Text("Max 50,000") },
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

                    // Quick bet buttons
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Pick a side and double your bet!  Payout: x2",
                        fontSize = 11.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Flip button
                    val parsedAmount = amount.toLongOrNull()
                    val canPlay = parsedAmount != null && parsedAmount > 0 && choice != null
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canPlay) Accent else TextMuted.copy(alpha = 0.3f))
                            .clickable(enabled = canPlay) {
                                if (parsedAmount != null && choice != null) {
                                    onPlay(parsedAmount, choice!!)
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Flip!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canPlay) SurfaceDark else TextMuted,
                        )
                    }
                }
            }
        }
    }
}

// ── Coin animation ──

@Composable
private fun CoinAnimation(targetResult: String) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Spin fast then land on the target side
        // Heads = even number of half-turns (0, 360, 720...), Tails = odd (180, 540, 900...)
        val extraSpins = 5 * 360f // 5 full rotations
        val finalAngle = if (targetResult == "heads") extraSpins else extraSpins + 180f
        rotation.animateTo(
            targetValue = finalAngle,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
        )
    }

    val currentRotation = rotation.value % 360f
    val showHeads = currentRotation < 90f || currentRotation > 270f

    Box(
        modifier = Modifier
            .size(100.dp)
            .graphicsLayer {
                rotationX = rotation.value
                cameraDistance = 12f * density
            },
        contentAlignment = Alignment.Center,
    ) {
        CoinFace(side = if (showHeads) "heads" else "tails", size = 100)
    }
}

@Composable
private fun CoinFace(side: String, size: Int) {
    val url = if (side == "heads") COIN_HEADS_URL else COIN_TAILS_URL
    AsyncImage(
        model = url,
        contentDescription = side,
        modifier = Modifier.size(size.dp).clip(CircleShape),
    )
}

// ── Choice button ──

@Composable
private fun ChoiceButton(
    label: String,
    imageUrl: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) Accent else SurfaceBorder
    val bgColor = if (selected) Accent.copy(alpha = 0.1f) else Color.Transparent

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = label,
            modifier = Modifier.size(48.dp).clip(CircleShape),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Accent else TextPrimary,
        )
    }
}
