package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.data.repository.SlotsResponse
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

private val ALL_SYMBOLS = listOf("🍒", "🍋", "🍊", "🍇", "💎", "7️⃣")

private fun generateReelStrip(initial: String): List<String> = listOf(initial)

private val SYMBOL_SPRITES = mapOf(
    "🍒" to "https://mg-api.ariedam.fr/assets/sprites/plants/Carrot.png",
    "🍋" to "https://mg-api.ariedam.fr/assets/sprites/plants/Lemon.png",
    "🍊" to "https://mg-api.ariedam.fr/assets/sprites/plants/Lychee.png",
    "🍇" to "https://mg-api.ariedam.fr/assets/sprites/plants/Grape.png",
    "💎" to "https://mg-api.ariedam.fr/assets/sprites/plants/Starweaver.png",
    "7️⃣" to "https://mg-api.ariedam.fr/assets/sprites/items/Shovel.png",
)

@Composable
fun SlotsGame(
    casinoBalance: Long?,
    result: SlotsResponse?,
    loading: Boolean,
    error: String?,
    onPlay: (amount: Long) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onResultShown: () -> Unit = {},
) {
    var amount by remember { mutableStateOf("") }
    var animating by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }

    // Each reel has its own strip of symbols and scroll offset
    val reelStrips = remember { List(3) { mutableStateOf(generateReelStrip("❓")) } }
    val reelOffsets = remember { List(3) { Animatable(0f) } }
    var reelsStopped by remember { mutableStateOf(listOf(false, false, false)) }

    // Animation when result arrives
    LaunchedEffect(result) {
        if (result != null && !showResult) {
            animating = true
            reelsStopped = listOf(false, false, false)

            // Build strips ending on the target symbol for each reel
            val stripSize = 20
            result.reels.forEachIndexed { idx, target ->
                val strip = buildList {
                    repeat(stripSize - 1) { add(ALL_SYMBOLS.random()) }
                    add(target) // last item is the final symbol
                }
                reelStrips[idx].value = strip
                reelOffsets[idx].snapTo(0f)
            }

            // Animate each reel with staggered stop
            val durations = listOf(1200, 1800, 2400)
            coroutineScope {
                durations.forEachIndexed { idx, dur ->
                    launch {
                        reelOffsets[idx].animateTo(
                            targetValue = 1f,
                            animationSpec = tween(dur, easing = EaseOutCubic),
                        )
                        reelsStopped = reelsStopped.toMutableList().also { it[idx] = true }
                    }
                }
            }

            // All reels done
            animating = false
            delay(300)
            showResult = true
            onResultShown()
        }
    }

    // Header
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onReset(); onBack() }) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Text("Slots", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.weight(1f))
        if (casinoBalance != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(numberFormat.format(casinoBalance), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = StatusConnected)
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // ── Slot machine ──
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Reels display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(2.dp, SurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                reelStrips.forEachIndexed { idx, stripState ->
                    if (idx > 0) {
                        Box(modifier = Modifier.width(2.dp).height(80.dp).background(SurfaceBorder))
                    }
                    // Single reel — vertically scrolling symbol
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val strip = stripState.value
                        val progress = reelOffsets[idx].value
                        val currentIndex = (progress * (strip.size - 1)).toInt().coerceIn(0, strip.lastIndex)
                        val symbol = strip[currentIndex]
                        val spriteUrl = SYMBOL_SPRITES[symbol]
                        if (spriteUrl != null) {
                            AsyncImage(model = spriteUrl, contentDescription = symbol, modifier = Modifier.size(48.dp))
                        } else {
                            Text(text = symbol, fontSize = 44.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // Payline info
            if (showResult && result != null && result.payline != "none") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (result.payline) {
                        "3-of-a-kind" -> "3 of a Kind! x${result.multiplier.toInt()}"
                        "2-of-a-kind" -> "2 of a Kind! x${result.multiplier}"
                        else -> ""
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = StatusConnecting,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                // ── Result ──
                showResult && result != null -> {
                    val won = result.won
                    val color = if (won) StatusConnected else StatusError
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
                                text = if (won) "You Won!" else "No luck",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = color,
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
                                    color = color,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Accent)
                            .clickable {
                                showResult = false
                                reelStrips.forEach { it.value = generateReelStrip("❓") }
                                onReset()
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Spin Again", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SurfaceDark)
                    }
                }

                // ── Spinning ──
                animating || loading -> {
                    Text("Spinning...", fontSize = 14.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── Bet UI ──
                else -> {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { new -> amount = new.filter { it.isDigit() } },
                        label = { Text("Bet amount") },
                        placeholder = { Text("Max 20,000") },
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
                    Text("Match 2 or 3 symbols to win!", fontSize = 11.sp, color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(8.dp))
                    PayTable()

                    Spacer(modifier = Modifier.height(12.dp))

                    val parsedAmount = amount.toLongOrNull()
                    val canPlay = parsedAmount != null && parsedAmount > 0
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canPlay) Accent else TextMuted.copy(alpha = 0.3f))
                            .clickable(enabled = canPlay) { parsedAmount?.let { onPlay(it) } }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Spin!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (canPlay) SurfaceDark else TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReelSymbol(symbol: String?, cellHeight: Int) {
    Box(
        modifier = Modifier.height(cellHeight.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (symbol == null) return@Box
        val spriteUrl = SYMBOL_SPRITES[symbol]
        if (spriteUrl != null) {
            AsyncImage(model = spriteUrl, contentDescription = symbol, modifier = Modifier.size(44.dp))
        } else {
            Text(text = symbol, fontSize = 40.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PayTable() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text("Pay Table", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        PayRowSprite("7️⃣", 3, "x50")
        PayRowSprite("💎", 3, "x25")
        PayRowSprite("🍇", 3, "x12")
        PayRowSprite("🍊", 3, "x8")
        PayRowSprite("🍋", 3, "x5")
        PayRowSprite("🍒", 3, "x3")
        Spacer(modifier = Modifier.height(4.dp))
        Text("2 matching: x1.5 — x15", fontSize = 10.sp, color = TextMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun PayRowSprite(symbol: String, count: Int, multiplier: String) {
    val url = SYMBOL_SPRITES[symbol]
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) {
                if (url != null) {
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(18.dp))
                } else {
                    Text(symbol, fontSize = 14.sp)
                }
            }
        }
        Text(multiplier, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusConnecting)
    }
}
