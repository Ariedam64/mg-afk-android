package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.data.repository.SlotsMachineResult
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ALL_SYMBOLS = listOf("\uD83C\uDF52", "\uD83C\uDF4B", "\uD83C\uDF4A", "\uD83C\uDF47", "\uD83D\uDC8E", "7\uFE0F\u20E3")

private val SYMBOL_SPRITES = mapOf(
    "\uD83C\uDF52" to "https://mg-api.ariedam.fr/assets/sprites/plants/Carrot.png",
    "\uD83C\uDF4B" to "https://mg-api.ariedam.fr/assets/sprites/plants/Banana.png",
    "\uD83C\uDF4A" to "https://mg-api.ariedam.fr/assets/sprites/plants/Pepper.png",
    "\uD83C\uDF47" to "https://mg-api.ariedam.fr/assets/sprites/plants/Sunflower.png",
    "\uD83D\uDC8E" to "https://mg-api.ariedam.fr/assets/sprites/plants/Starweaver.png",
    "7\uFE0F\u20E3" to BREAD_SPRITE_URL,
)

@Composable
fun SlotsGame(
    casinoBalance: Long?,
    result: SlotsResponse?,
    loading: Boolean,
    error: String?,
    onPlay: (amount: Long, machines: Int) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onResultShown: () -> Unit = {},
) {
    val sound = rememberSoundManager()
    var amount by remember { mutableStateOf("") }
    var machineCount by remember { mutableIntStateOf(1) }
    // Game phase: idle → spinning → done
    var phase by remember { mutableStateOf("idle") } // "idle" | "spinning" | "done"
    var showBanner by remember { mutableStateOf(false) }
    var lastAmount by remember { mutableStateOf(0L) }
    var lastMachines by remember { mutableIntStateOf(1) }

    val machineCountFromResult = result?.machines?.size ?: 1

    val maxReels = 4 * 3
    val reelStrips = remember { List(maxReels) { mutableStateOf(listOf("\u2753")) } }
    val reelScrollPos = remember { List(maxReels) { Animatable(0f) } }
    val reelPopScale = remember { List(maxReels) { Animatable(1f) } }

    // Start spinning when loading begins
    LaunchedEffect(loading) {
        if (loading) {
            phase = "spinning"
            showBanner = false
        }
    }

    // Animate when result arrives and we're in spinning phase
    LaunchedEffect(result) {
        if (result == null || phase != "spinning") return@LaunchedEffect

        // Reset all reels
        for (i in 0 until maxReels) {
            reelScrollPos[i].snapTo(0f)
            reelPopScale[i].snapTo(1f)
            reelStrips[i].value = listOf("\u2753")
        }

        sound.play(Sfx.SLOTS_LEVER)
        delay(300)
        sound.play(Sfx.SLOTS_SPINNING, 0.6f, loop = true)

        // Every reel is fully independent — random strip size + duration
        val totalMachines = result.machines.size
        val targetIndices = mutableListOf<Int>()
        val randomDurations = mutableListOf<Int>()

        result.machines.forEachIndexed { machIdx, machine ->
            machine.reels.forEachIndexed { reelIdx, target ->
                val globalIdx = machIdx * 3 + reelIdx
                val thisStripSize = (25..70).random()
                val thisTargetIdx = thisStripSize - 2
                targetIndices.add(thisTargetIdx)
                randomDurations.add((2500..5000).random())
                val strip = buildList {
                    repeat(thisStripSize - 2) { add(ALL_SYMBOLS.random()) }
                    add(target)
                    add(ALL_SYMBOLS.random())
                }
                reelStrips[globalIdx].value = strip
                reelScrollPos[globalIdx].snapTo(0f)
                reelPopScale[globalIdx].snapTo(1f)
            }
        }

        coroutineScope {
            result.machines.forEachIndexed { machIdx, _ ->
                (0..2).forEach { reelIdx ->
                    val globalIdx = machIdx * 3 + reelIdx
                    val totalDelay = (0L..200L).random()
                    val isLastReel = machIdx == totalMachines - 1 && reelIdx == 2
                    launch {
                        delay(totalDelay)
                        val thisTarget = targetIndices[globalIdx]
                        reelScrollPos[globalIdx].animateTo(
                            thisTarget - 0.08f,
                            tween(randomDurations[globalIdx], easing = { t ->
                                val inv = 1f - t
                                1f - inv * inv * inv * inv
                            }),
                        )
                        reelScrollPos[globalIdx].snapTo(thisTarget.toFloat())

                        sound.play(Sfx.REEL_STOP, 0.6f)
                        reelPopScale[globalIdx].snapTo(1.18f)
                        reelPopScale[globalIdx].animateTo(1f, spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ))

                        if (isLastReel) {
                            sound.stop(Sfx.SLOTS_SPINNING)
                        }
                    }
                }
            }
        }

        phase = "done"
        delay(300)
        sound.play(if (result.won) Sfx.WIN_COINS else Sfx.LOSE)
        onResultShown()
        delay(200)
        showBanner = true
    }

    GameHeader(title = "Slots", casinoBalance = casinoBalance, onBack = { onReset(); onBack() })

    Spacer(modifier = Modifier.height(8.dp))

    // Content (parent is already scrollable)
    Column {
        // Machines display
        val displayCount = if (result != null) machineCountFromResult else machineCount
        val isMulti = displayCount > 1

        if (phase != "idle") {
            // Show machine(s)
            if (isMulti) {
                for (machIdx in 0 until displayCount) {
                    SlotMachine(
                        machineIndex = machIdx,
                        machineResult = result?.machines?.getOrNull(machIdx),
                        reelStrips = reelStrips,
                        reelScrollPositions = reelScrollPos,
                        reelPopScales = reelPopScale,
                        showResult = phase == "done",
                        compact = true,
                    )
                    if (machIdx < displayCount - 1) Spacer(modifier = Modifier.height(6.dp))
                }
            } else {
                // Single machine — full width
                SlotMachine(
                    machineIndex = 0,
                    machineResult = result?.machines?.getOrNull(0),
                    reelStrips = reelStrips,
                    reelScrollPositions = reelScrollPos,
                    reelPopScales = reelPopScale,
                    showResult = phase == "done",
                    compact = false,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Spinning text or Bet UI (no card when result is showing — popup handles it)
        if (phase == "spinning") {
            AppCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Spinning...", fontSize = 14.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        } else if (phase == "idle") {
            AppCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                        // Machine count selector
                        Text("Machines", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            (1..4).forEach { count ->
                                val isSelected = machineCount == count
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Accent.copy(alpha = 0.15f) else Accent.copy(alpha = 0.04f))
                                        .border(1.5.dp, if (isSelected) Accent else SurfaceBorder, RoundedCornerShape(8.dp))
                                        .clickable { machineCount = count }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$count",
                                        fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Accent else TextPrimary,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        BetInput(amount = amount, onAmountChange = { amount = it }, balance = casinoBalance, maxBet = 20_000, label = "Bet per machine")

                        // Total bet display
                        val parsedAmount = amount.toLongOrNull()
                        if (parsedAmount != null && parsedAmount > 0 && machineCount > 1) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Total bet: ${numberFormat.format(parsedAmount * machineCount)} ($machineCount x ${numberFormat.format(parsedAmount)})",
                                fontSize = 11.sp, color = TextMuted,
                            )
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

                        val canPlay = parsedAmount != null && parsedAmount > 0
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (canPlay) Accent else TextMuted.copy(alpha = 0.3f))
                                .clickable(enabled = canPlay) {
                                    parsedAmount?.let {
                                        lastAmount = it
                                        lastMachines = machineCount
                                        onPlay(it, machineCount)
                                    }
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Spin!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (canPlay) SurfaceDark else TextMuted)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

    if (phase == "done" && result != null) {
        val subtitle = if (machineCountFromResult > 1)
            "${result.machines.count { it.won }}/$machineCountFromResult machines won"
        else null
        ResultPopup(
            visible = showBanner,
            won = result.won,
            title = if (result.totalPayout > result.totalBet) "You Won!" else if (result.totalPayout == result.totalBet) "Break Even" else "No luck",
            subtitle = subtitle,
            bet = result.totalBet,
            payout = result.totalPayout,
            onReplay = {
                phase = "idle"; showBanner = false
                reelStrips.forEach { it.value = listOf("\u2753") }
                onReset()
                onPlay(lastAmount, lastMachines)
            },
            onBack = { phase = "idle"; showBanner = false; onReset() },
        )
    }
}

// ── Single slot machine reels ──

@Composable
private fun SlotMachine(
    machineIndex: Int,
    machineResult: SlotsMachineResult?,
    reelStrips: List<androidx.compose.runtime.MutableState<List<String>>>,
    reelScrollPositions: List<Animatable<Float, *>>,
    reelPopScales: List<Animatable<Float, *>>,
    showResult: Boolean,
    compact: Boolean,
) {
    val reelHeight = if (compact) 110 else 150
    val cellHeightDp = if (compact) 38 else 52
    val iconSize = if (compact) 32 else 46

    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (compact) {
                Text("#${machineIndex + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Spacer(modifier = Modifier.height(2.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(if (compact) 10.dp else 16.dp))
                    .background(SurfaceDark)
                    .border(1.5.dp, SurfaceBorder, RoundedCornerShape(if (compact) 10.dp else 16.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (0..2).forEach { reelIdx ->
                    val globalIdx = machineIndex * 3 + reelIdx
                    if (reelIdx > 0) {
                        Box(modifier = Modifier.width(1.dp).height(reelHeight.dp).background(SurfaceBorder))
                    }

                    // Continuous scroll reel with pop scale
                    val popScale = reelPopScales[globalIdx].value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(reelHeight.dp)
                            .clipToBounds()
                            .graphicsLayer {
                                scaleX = popScale
                                scaleY = popScale
                            },
                    ) {
                        val strip = reelStrips[globalIdx].value
                        val scrollPos = reelScrollPositions[globalIdx].value
                        val cellHeightPx = cellHeightDp

                        val centerIdx = scrollPos
                        val firstVisible = (centerIdx - 2).toInt().coerceAtLeast(0)
                        val lastVisible = (centerIdx + 2).toInt().coerceAtMost(strip.lastIndex)

                        for (i in firstVisible..lastVisible) {
                            val distFromCenter = i.toFloat() - centerIdx
                            val yOffset = (distFromCenter * cellHeightPx).toInt()
                            val absDist = kotlin.math.abs(distFromCenter)
                            val alpha = (1f - absDist * 0.55f).coerceIn(0.15f, 1f)
                            val scale = (1f - absDist * 0.12f).coerceIn(0.75f, 1f)

                            val symbol = strip.getOrNull(i) ?: continue
                            val spriteUrl = SYMBOL_SPRITES[symbol]

                            Box(
                                modifier = Modifier
                                    .size(width = 80.dp, height = cellHeightDp.dp)
                                    .offset { IntOffset(0, (yOffset * density).toInt()) }
                                    .align(Alignment.Center)
                                    .graphicsLayer {
                                        this.alpha = alpha
                                        scaleX = scale
                                        scaleY = scale
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (spriteUrl != null) {
                                    AsyncImage(
                                        model = spriteUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(iconSize.dp),
                                    )
                                } else {
                                    Text(symbol, fontSize = (iconSize - 4).sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }

            // Payline result
            if (showResult && machineResult != null && machineResult.payline != "none") {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    when (machineResult.payline) {
                        "3-of-a-kind" -> "x${machineResult.multiplier.toInt()}"
                        "2-of-a-kind" -> "x${machineResult.multiplier}"
                        else -> ""
                    },
                    fontSize = if (compact) 11.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = StatusConnecting,
                )
            }
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
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(1f))
            Text("3x", fontSize = 9.sp, color = TextMuted.copy(alpha = 0.6f), modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
            Text("2x", fontSize = 9.sp, color = TextMuted.copy(alpha = 0.6f), modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        }
        PayRowSprite("7\uFE0F\u20E3", 3, "x150", "x12")
        PayRowSprite("\uD83D\uDC8E", 3, "x60", "x5")
        PayRowSprite("\uD83C\uDF47", 3, "x30", "x2")
        PayRowSprite("\uD83C\uDF4A", 3, "x15", "x1.2")
        PayRowSprite("\uD83C\uDF4B", 3, "x10", "x0.7")
        PayRowSprite("\uD83C\uDF52", 3, "x5", "x0.7")
    }
}

@Composable
private fun PayRowSprite(symbol: String, count: Int, multiplier3: String, multiplier2: String) {
    val url = SYMBOL_SPRITES[symbol]
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            repeat(count) {
                if (url != null) {
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(18.dp))
                } else {
                    Text(symbol, fontSize = 14.sp)
                }
            }
        }
        Text(multiplier3, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = StatusConnecting, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
        Text(multiplier2, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextMuted, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
}
