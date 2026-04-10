package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
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
import java.text.NumberFormat
import java.util.Locale

private val numberFormat = NumberFormat.getNumberInstance(Locale.US)

private val ALL_SYMBOLS = listOf("\uD83C\uDF52", "\uD83C\uDF4B", "\uD83C\uDF4A", "\uD83C\uDF47", "\uD83D\uDC8E", "7\uFE0F\u20E3")

private val SYMBOL_SPRITES = mapOf(
    "\uD83C\uDF52" to "https://mg-api.ariedam.fr/assets/sprites/plants/Carrot.png",
    "\uD83C\uDF4B" to "https://mg-api.ariedam.fr/assets/sprites/plants/Lemon.png",
    "\uD83C\uDF4A" to "https://mg-api.ariedam.fr/assets/sprites/plants/Lychee.png",
    "\uD83C\uDF47" to "https://mg-api.ariedam.fr/assets/sprites/plants/Grape.png",
    "\uD83D\uDC8E" to "https://mg-api.ariedam.fr/assets/sprites/plants/Starweaver.png",
    "7\uFE0F\u20E3" to "https://mg-api.ariedam.fr/assets/sprites/items/Shovel.png",
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
    var animating by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var showBanner by remember { mutableStateOf(false) }
    // Remember last play params for replay
    var lastAmount by remember { mutableStateOf(0L) }
    var lastMachines by remember { mutableIntStateOf(1) }

    val machineCountFromResult = result?.machines?.size ?: 1

    // Per-machine animation state (max 5 machines * 3 reels = 15 reels)
    val maxReels = 4 * 3
    val reelStrips = remember { List(maxReels) { mutableStateOf(listOf("\u2753")) } }
    val reelOffsets = remember { List(maxReels) { Animatable(0f) } }
    val reelScales = remember { List(maxReels) { Animatable(1f) } }

    // Animate all machines when result arrives
    LaunchedEffect(result) {
        if (result != null && !showResult) {
            animating = true
            showBanner = false
            sound.play(Sfx.SLOTS_LEVER)
            delay(200) // slight delay before spinning sound kicks in
            sound.play(Sfx.SLOTS_SPINNING, 0.6f, loop = true)

            val stripSize = 25
            val totalMachines = result.machines.size

            // Build reel strips for each machine
            result.machines.forEachIndexed { machIdx, machine ->
                machine.reels.forEachIndexed { reelIdx, target ->
                    val globalIdx = machIdx * 3 + reelIdx
                    val strip = buildList {
                        repeat(stripSize - 1) { add(ALL_SYMBOLS.random()) }
                        add(target)
                    }
                    reelStrips[globalIdx].value = strip
                    reelOffsets[globalIdx].snapTo(0f)
                    reelScales[globalIdx].snapTo(1f)
                }
            }

            // Animate with stagger: each machine starts slightly after the previous
            val baseDurations = listOf(1800, 2600, 3400)
            coroutineScope {
                result.machines.forEachIndexed { machIdx, _ ->
                    val machineDelay = machIdx * 400L // stagger between machines
                    baseDurations.forEachIndexed { reelIdx, dur ->
                        val globalIdx = machIdx * 3 + reelIdx
                        launch {
                            delay(machineDelay)
                            reelOffsets[globalIdx].animateTo(1f, tween(dur, easing = EaseOutCubic))
                            sound.play(Sfx.REEL_STOP, 0.5f)
                            reelScales[globalIdx].animateTo(1.12f, tween(80))
                            reelScales[globalIdx].animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                }
            }

            animating = false
            sound.stop(Sfx.SLOTS_SPINNING)
            delay(300)
            showResult = true
            sound.play(if (result.won) Sfx.WIN_COINS else Sfx.LOSE)
            onResultShown()
            delay(200)
            showBanner = true
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

    // Content (parent is already scrollable)
    Column {
        // Machines display
        val displayCount = if (result != null) machineCountFromResult else machineCount
        val isMulti = displayCount > 1

        if (result != null || animating || loading) {
            // Show machine(s)
            if (isMulti) {
                for (machIdx in 0 until displayCount) {
                    SlotMachine(
                        machineIndex = machIdx,
                        machineResult = result?.machines?.getOrNull(machIdx),
                        reelStrips = reelStrips,
                        reelOffsets = reelOffsets,
                        reelScales = reelScales,
                        showResult = showResult,
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
                    reelOffsets = reelOffsets,
                    reelScales = reelScales,
                    showResult = showResult,
                    compact = false,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Result / Spinning / Bet UI
        AppCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when {
                    showResult && result != null -> {
                        // Per-machine mini results for multi
                        if (machineCountFromResult > 1) {
                            result.machines.forEachIndexed { idx, machine ->
                                val color = if (machine.won) StatusConnected else TextMuted
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("#${idx + 1}", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                    if (machine.payline != "none") {
                                        Text(
                                            when (machine.payline) {
                                                "3-of-a-kind" -> "x${machine.multiplier.toInt()}"
                                                "2-of-a-kind" -> "x${machine.multiplier}"
                                                else -> ""
                                            },
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusConnecting,
                                        )
                                    } else {
                                        Text("miss", fontSize = 11.sp, color = TextMuted)
                                    }
                                    Text(
                                        if (machine.won) "+${numberFormat.format(machine.payout)}" else "0",
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace, color = color,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Total result banner
                        AnimatedVisibility(
                            visible = showBanner,
                            enter = scaleIn(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                initialScale = 0.5f,
                            ) + fadeIn(),
                        ) {
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
                                        if (won) "You Won!" else "No luck",
                                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (won) "+${numberFormat.format(result.totalPayout)}" else "-${numberFormat.format(result.totalBet)}",
                                            fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace, color = color,
                                        )
                                    }
                                    if (machineCountFromResult > 1) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            "${result.machines.count { it.won }}/${machineCountFromResult} machines won",
                                            fontSize = 11.sp, color = TextMuted,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Back + Replay
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
                                            showResult = false; showBanner = false
                                            reelStrips.forEach { it.value = listOf("\u2753") }
                                            onReset()
                                            onPlay(lastAmount, lastMachines)
                                        }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Replay ${numberFormat.format(result.totalBet)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SurfaceDark)
                                }
                            }
                        }
                    }

                    animating || loading -> {
                        Text("Spinning...", fontSize = 14.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Bet UI
                    else -> {
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

                        OutlinedTextField(
                            value = amount,
                            onValueChange = { new -> amount = new.filter { it.isDigit() } },
                            label = { Text("Bet per machine") },
                            placeholder = { Text("Max 20,000") },
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
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// ── Single slot machine reels ──

@Composable
private fun SlotMachine(
    machineIndex: Int,
    machineResult: SlotsMachineResult?,
    reelStrips: List<androidx.compose.runtime.MutableState<List<String>>>,
    reelOffsets: List<Animatable<Float, *>>,
    reelScales: List<Animatable<Float, *>>,
    showResult: Boolean,
    compact: Boolean,
) {
    val reelHeight = if (compact) 110 else 150
    val cellHeight = if (compact) 36 else 50
    val iconSize = if (compact) 32 else 46
    val dimmedIconSize = if (compact) 28 else 40

    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Machine label for multi
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(reelHeight.dp)
                            .clipToBounds(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val strip = reelStrips[globalIdx].value
                        val progress = reelOffsets[globalIdx].value
                        val currentIndex = (progress * (strip.size - 1)).toInt().coerceIn(0, strip.lastIndex)
                        val fractional = (progress * (strip.size - 1)) - currentIndex
                        val scrollOffset = -(fractional * cellHeight).toInt()

                        Column(
                            modifier = Modifier
                                .offset { IntOffset(0, scrollOffset) }
                                .graphicsLayer {
                                    scaleX = reelScales[globalIdx].value
                                    scaleY = reelScales[globalIdx].value
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val aboveIdx = (currentIndex - 1).coerceAtLeast(0)
                            ReelSymbol(strip.getOrNull(aboveIdx), cellHeight, dimmed = true, iconSize = dimmedIconSize)
                            ReelSymbol(strip.getOrNull(currentIndex), cellHeight, dimmed = false, iconSize = iconSize)
                            val belowIdx = (currentIndex + 1).coerceAtMost(strip.lastIndex)
                            ReelSymbol(strip.getOrNull(belowIdx), cellHeight, dimmed = true, iconSize = dimmedIconSize)
                        }
                    }
                }
            }

            // Payline result for this machine
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
private fun ReelSymbol(symbol: String?, cellHeight: Int, dimmed: Boolean, iconSize: Int = if (dimmed) 40 else 46) {
    Box(
        modifier = Modifier.height(cellHeight.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (symbol == null) return@Box
        val spriteUrl = SYMBOL_SPRITES[symbol]
        val alpha = if (dimmed) 0.35f else 1f
        val scale = if (dimmed) 0.85f else 1f
        if (spriteUrl != null) {
            AsyncImage(
                model = spriteUrl, contentDescription = symbol,
                modifier = Modifier.size(iconSize.dp).graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale },
            )
        } else {
            Text(
                symbol, fontSize = if (dimmed) (iconSize - 6).sp else (iconSize - 2).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale },
            )
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
