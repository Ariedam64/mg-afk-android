package com.mgafk.app.ui.screens.minigames

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.EaseOutBounce
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.data.repository.EggHatchConfig
import com.mgafk.app.data.repository.EggHatchPet
import com.mgafk.app.data.repository.EggHatchResponse
import com.mgafk.app.data.repository.EggInfo
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Format large numbers compactly: 1,234 → 1.2k, 1,234,567 → 1.2M */
private fun formatCompact(value: Long): String = when {
    value >= 1_000_000 -> "${String.format("%.1f", value / 1_000_000.0)}M"
    value >= 10_000 -> "${value / 1_000}k"
    value >= 1_000 -> "${String.format("%.1f", value / 1_000.0)}k"
    else -> value.toString()
}

// Rarity colors
private val RarityCommon = Color(0xFF9CA3AF)
private val RarityUncommon = Color(0xFF4ADE80)
private val RarityRare = Color(0xFF60A5FA)
private val RarityLegendary = Color(0xFFFBBF24)
private val RarityMythic = Color(0xFFC084FC)

// Mutation colors
private val GoldColor = Color(0xFFFFD700)
private val GoldDark = Color(0xFFB8860B)
private val RainbowColors = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77),
    Color(0xFF4D96FF), Color(0xFFC084FC), Color(0xFFFF6B9D),
)

/** Pick a rainbow color cycling through the palette based on index or time. */
private fun rainbowAt(index: Int) = RainbowColors[index % RainbowColors.size]

private fun rarityColor(rarity: String): Color = when (rarity.lowercase()) {
    "common" -> RarityCommon
    "uncommon" -> RarityUncommon
    "rare" -> RarityRare
    "legendary" -> RarityLegendary
    "mythic", "mythical" -> RarityMythic
    else -> RarityCommon
}

private data class HatchParticle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, var maxLife: Float,
    var size: Float, var color: Color,
)

/** A pet sprite flying out of the egg with its own position/animation. */
private data class FlyingPet(
    val sprite: String,
    val mutation: String,
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var scale: Float = 0f,
    var targetScale: Float = 1f,
    var alpha: Float = 0f,
    var settled: Boolean = false,
)

@Composable
fun EggHatchGame(
    casinoBalance: Long?,
    eggs: List<EggInfo>,
    eggConfig: EggHatchConfig,
    eggsLoading: Boolean,
    result: EggHatchResponse?,
    loading: Boolean,
    error: String?,
    onFetchEggs: () -> Unit,
    onPlay: (eggType: String, count: Int) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onResultShown: () -> Unit = {},
) {
    val sound = rememberSoundManager()
    var selectedEgg by remember { mutableStateOf<EggInfo?>(null) }
    var count by remember { mutableIntStateOf(1) }

    // Animation state
    var phase by remember { mutableStateOf("idle") } // idle | hatching | revealing | result
    var revealedIndex by remember { mutableIntStateOf(-1) }

    // Egg shake animation
    val eggRotation = remember { Animatable(0f) }
    val eggScale = remember { Animatable(1f) }
    val crackProgress = remember { Animatable(0f) }
    val petScale = remember { Animatable(0f) }
    val petAlpha = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }

    // Particles & flying pets
    var particles by remember { mutableStateOf(emptyList<HatchParticle>()) }
    var flyingPets by remember { mutableStateOf(emptyList<FlyingPet>()) }

    // Fetch eggs on first load
    LaunchedEffect(Unit) { onFetchEggs() }

    // Particle + flying pets tick
    LaunchedEffect(phase) {
        if (phase == "hatching" || phase == "revealing") {
            while (true) {
                particles = particles.mapNotNull { p ->
                    val decay = 0.016f / p.maxLife
                    val newLife = p.life - decay
                    if (newLife <= 0f) null
                    else p.copy(
                        x = p.x + p.vx,
                        y = p.y + p.vy,
                        vy = p.vy + 0.4f,
                        life = newLife,
                    )
                }
                // Animate flying pets — decelerate and settle
                flyingPets = flyingPets.map { fp ->
                    if (fp.settled) fp
                    else {
                        val newX = fp.x + fp.vx
                        val newY = fp.y + fp.vy
                        val newVx = fp.vx * 0.92f
                        val newVy = fp.vy * 0.92f
                        val newScale = minOf(fp.scale + 0.08f, fp.targetScale)
                        val newAlpha = minOf(fp.alpha + 0.06f, 1f)
                        val isSettled = kotlin.math.abs(newVx) < 0.3f && kotlin.math.abs(newVy) < 0.3f
                        fp.copy(
                            x = newX, y = newY,
                            vx = newVx, vy = newVy,
                            scale = newScale, alpha = newAlpha,
                            settled = isSettled,
                        )
                    }
                }
                delay(16)
            }
        }
    }

    // Main animation sequence
    LaunchedEffect(result) {
        if (result == null || phase == "result") return@LaunchedEffect

        phase = "hatching"
        revealedIndex = -1
        particles = emptyList()

        // Reset animations
        eggRotation.snapTo(0f)
        eggScale.snapTo(1f)
        crackProgress.snapTo(0f)
        petScale.snapTo(0f)
        petAlpha.snapTo(0f)
        flashAlpha.snapTo(0f)
        flyingPets = emptyList()

        sound.play(Sfx.BUTTON)

        // Phase 1: Egg shaking (intensifies over time)
        launch {
            repeat(12) { i ->
                val intensity = 3f + i * 1.5f
                eggRotation.animateTo(intensity, tween(60))
                eggRotation.animateTo(-intensity, tween(60))
            }
            eggRotation.animateTo(0f, tween(40))
        }

        // Crack progress increases during shaking
        launch {
            crackProgress.animateTo(1f, tween(1400, easing = EaseInOutCubic))
        }

        delay(1500)

        // Phase 2: Egg breaks — flash + explode
        val firstPet = result.hatches.firstOrNull()
        val isMutation = firstPet?.mutation != "Normal"

        sound.play(Sfx.REVEAL)

        // Flash based on mutation
        launch {
            flashAlpha.snapTo(if (isMutation) 1f else 0.7f)
            flashAlpha.animateTo(0f, tween(if (isMutation) 800 else 400))
        }

        // Egg scale out
        launch {
            eggScale.animateTo(1.3f, tween(100))
            eggScale.animateTo(0f, tween(200))
        }

        // Spawn explosion particles
        val mutColor = when (firstPet?.mutation) {
            "Gold" -> GoldColor
            "Rainbow" -> RainbowColors.random()
            else -> Color.White
        }
        particles = List(40) {
            val angle = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 3f + Random.nextFloat() * 8f
            HatchParticle(
                x = 0f, y = 0f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 4f,
                life = 1f,
                maxLife = 0.4f + Random.nextFloat() * 0.6f,
                size = 3f + Random.nextFloat() * 6f,
                color = if (isMutation) mutColor else rarityColor(firstPet?.petRarity ?: "Common"),
            )
        }

        delay(300)

        // Phase 3: Pet reveal
        val hasMutation = result.hatches.any { it.mutation != "Normal" }
        sound.play(if (hasMutation) Sfx.BIG_WIN else Sfx.WIN)

        if (result.count == 1) {
            // Single hatch: show pet bounce-in then details
            phase = "revealing"
            launch { petAlpha.animateTo(1f, tween(200)) }
            petScale.snapTo(0.3f)
            petScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
            delay(1200)
        } else {
            // Multi-hatch: skip reveal, jump straight to grid
            delay(400)
        }

        // Phase 4: Show result
        phase = "result"
        onResultShown()

    }

    GameHeader(title = "Egg Hatch", casinoBalance = casinoBalance, onBack = { onReset(); onBack() })

    Spacer(modifier = Modifier.height(8.dp))

    when {
        // ── Hatching / Revealing / Result animation ──
        phase == "hatching" || phase == "revealing" || (phase == "result" && result != null) -> {
            HatchingView(
                result = result!!,
                phase = phase,
                eggRotation = eggRotation.value,
                eggScale = eggScale.value,
                crackProgress = crackProgress.value,
                petScale = petScale.value,
                petAlpha = petAlpha.value,
                flashAlpha = flashAlpha.value,
                particles = particles,
                flyingPets = flyingPets,
                onReplay = {
                    val lastEgg = selectedEgg
                    val lastCount = count
                    phase = "idle"; onReset()
                    if (lastEgg != null) onPlay(lastEgg.key, lastCount)
                },
                onBack = { phase = "idle"; onReset() },
            )
        }

        // ── Loading ──
        loading -> {
            AppCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Accent, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Hatching eggs...", fontSize = 13.sp, color = TextMuted)
                }
            }
        }

        // ── Egg selection UI ──
        else -> {
            EggSelectionView(
                eggs = eggs,
                eggConfig = eggConfig,
                eggsLoading = eggsLoading,
                selectedEgg = selectedEgg,
                count = count,
                casinoBalance = casinoBalance,
                error = error,
                onSelectEgg = { egg -> selectedEgg = egg; sound.play(Sfx.BUTTON) },
                onCountChange = { count = it },
                onHatch = {
                    if (selectedEgg != null) {
                        onPlay(selectedEgg!!.key, count)
                    }
                },
            )
        }
    }
}

// ── Egg Selection View ──

@Composable
private fun EggSelectionView(
    eggs: List<EggInfo>,
    eggConfig: EggHatchConfig,
    eggsLoading: Boolean,
    selectedEgg: EggInfo?,
    count: Int,
    casinoBalance: Long?,
    error: String?,
    onSelectEgg: (EggInfo) -> Unit,
    onCountChange: (Int) -> Unit,
    onHatch: () -> Unit,
) {
    // Egg grid
    AppCard(title = "Choose an Egg") {
        if (eggsLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Accent, strokeWidth = 2.dp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                eggs.forEach { egg ->
                    EggTile(
                        egg = egg,
                        selected = selectedEgg?.key == egg.key,
                        onClick = { onSelectEgg(egg) },
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Count + Hatch button
    if (selectedEgg != null) {
        AppCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Selected egg info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = selectedEgg.sprite,
                        contentDescription = selectedEgg.name,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selectedEgg.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = rarityColor(selectedEgg.rarity),
                        )
                        Text(
                            "${numberFormat.format(selectedEgg.price)} breads each",
                            fontSize = 12.sp,
                            color = TextMuted,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Count selector
                Text("Quantity", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                val maxCount = if (casinoBalance != null && selectedEgg.price > 0) {
                    minOf(100, (casinoBalance / selectedEgg.price).toInt()).coerceAtLeast(1)
                } else 1

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(1, 5, 10, 25, 50).forEach { value ->
                        val isSelected = count == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Accent.copy(alpha = 0.15f) else Accent.copy(alpha = 0.04f))
                                .border(
                                    1.dp,
                                    if (isSelected) Accent.copy(alpha = 0.4f) else Accent.copy(alpha = 0.12f),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onCountChange(value) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$value",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Accent else TextMuted,
                            )
                        }
                    }
                    // Max button
                    val isMaxSelected = count == maxCount && listOf(1, 5, 10, 25, 50).none { it == count }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMaxSelected) StatusConnected.copy(alpha = 0.15f) else StatusConnected.copy(alpha = 0.04f))
                            .border(
                                1.dp,
                                if (isMaxSelected) StatusConnected.copy(alpha = 0.4f) else StatusConnected.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onCountChange(maxCount) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Max",
                            fontSize = 12.sp,
                            fontWeight = if (isMaxSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isMaxSelected) StatusConnected else TextMuted,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Total cost
                val totalCost = selectedEgg.price * count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Total cost", fontSize = 12.sp, color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = BREAD_SPRITE_URL,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            numberFormat.format(totalCost),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                        )
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error, fontSize = 11.sp, color = StatusError)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hatch button
                val canHatch = casinoBalance != null && casinoBalance >= totalCost
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (canHatch) Accent else TextMuted.copy(alpha = 0.3f))
                        .clickable(enabled = canHatch) { onHatch() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (count == 1) "Hatch!" else "Hatch x$count!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canHatch) SurfaceDark else TextMuted,
                    )
                }

                if (casinoBalance != null && casinoBalance < totalCost) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Insufficient balance",
                        fontSize = 11.sp,
                        color = StatusError,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Pets list for selected egg
        Spacer(modifier = Modifier.height(8.dp))
        EggPetsPreview(egg = selectedEgg, config = eggConfig)
    }
}

// ── Egg tile in selection ──

@Composable
private fun EggTile(egg: EggInfo, selected: Boolean, onClick: () -> Unit) {
    val rColor = rarityColor(egg.rarity)
    val borderColor = if (selected) rColor else SurfaceBorder
    val bgColor = if (selected) rColor.copy(alpha = 0.08f) else Color.Transparent

    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = egg.sprite,
            contentDescription = egg.name,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            egg.name.replace(" Egg", ""),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = rColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            numberFormat.format(egg.price),
            fontSize = 9.sp,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
        )
    }
}

// ── Preview of pets in egg ──

@Composable
private fun EggPetsPreview(egg: EggInfo, config: EggHatchConfig) {
    AppCard(title = "Possible Pets") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Explanation
            Text(
                "Rarer pets and abilities = higher payout. Gold and Rainbow mutations multiply the reward.",
                fontSize = 10.sp,
                color = TextMuted,
            )

            egg.pets.forEach { pet ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceDark)
                        .padding(8.dp),
                ) {
                    // Pet row: sprite + name + chance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = pet.sprite,
                            contentDescription = pet.name,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    pet.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = rarityColor(pet.rarity),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "x${String.format("%.2f", pet.baseMult)}",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                        Text(
                            pet.chance,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = rarityColor(pet.rarity),
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    // Abilities with dot + name + chance
                    if (pet.abilities.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        pet.abilities.forEach { ability ->
                            Row(
                                modifier = Modifier.padding(start = 38.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(abilityColor(ability.key)),
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    ability.name,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "x${String.format("%.2f", ability.bonus)}",
                                    fontSize = 9.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${String.format("%.0f", ability.chance * 100)}%",
                                    fontSize = 10.sp,
                                    color = if (ability.chance < 0.2) StatusConnecting else TextMuted,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }

            // Mutations
            val normalChance = 1.0 - config.goldChance - config.rainbowChance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Normal
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Normal", fontSize = 9.sp, color = TextMuted)
                    Text(
                        "${String.format("%.1f", normalChance * 100)}%",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace,
                    )
                    Text("x1", fontSize = 9.sp, color = TextMuted)
                }
                // Gold
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SpriteImage(url = mutationSpriteUrl("Gold"), size = 14.dp, contentDescription = "Gold")
                    Text(
                        "${String.format("%.1f", config.goldChance * 100)}%",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldColor, fontFamily = FontFamily.Monospace,
                    )
                    Text("x${config.goldMultiplier}", fontSize = 9.sp, color = GoldColor)
                }
                // Rainbow
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SpriteImage(url = mutationSpriteUrl("Rainbow"), size = 14.dp, contentDescription = "Rainbow")
                    Text(
                        "${String.format("%.1f", config.rainbowChance * 100)}%",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RainbowColors[4], fontFamily = FontFamily.Monospace,
                    )
                    Text("x${config.rainbowMultiplier}", fontSize = 9.sp, color = RainbowColors[4])
                }
            }

            // STR multiplier
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("STR factor  ", fontSize = 10.sp, color = TextMuted)
                Text(
                    "x${String.format("%.2f", config.strFactorRange.min)} – x${String.format("%.2f", config.strFactorRange.max)}",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent, fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** Maps ability key to a color badge, matching the pet section style. */
private fun abilityColor(abilityId: String): Color {
    val id = abilityId.lowercase().replace(Regex("[\\s_-]+"), "")
    return when {
        id.startsWith("moonkisser") -> Color(0xFFFAA623)
        id.startsWith("dawnkisser") -> Color(0xFFA25CF2)
        id.startsWith("producescaleboost") || id.startsWith("snowycropsizeboost") -> Color(0xFF228B22)
        id.startsWith("plantgrowthboost") || id.startsWith("snowyplantgrowthboost") ||
            id.startsWith("dawnplantgrowthboost") || id.startsWith("amberplantgrowthboost") -> Color(0xFF008080)
        id.startsWith("egggrowthboost") || id.startsWith("snowyegggrowthboost") -> Color(0xFFB45AF0)
        id.startsWith("petageboost") -> Color(0xFF9370DB)
        id.startsWith("pethatchsizeboost") -> Color(0xFF800080)
        id.startsWith("petxpboost") || id.startsWith("snowypetxpboost") -> Color(0xFF1E90FF)
        id.startsWith("hungerboost") || id.startsWith("snowyhungerboost") -> Color(0xFFFF1493)
        id.startsWith("hungerrestore") || id.startsWith("snowyhungerrestore") -> Color(0xFFFF69B4)
        id.startsWith("sellboost") -> Color(0xFFDC143C)
        id.startsWith("coinfinder") || id.startsWith("snowycoinfinder") -> Color(0xFFB49600)
        id.startsWith("seedfinder") -> Color(0xFFA86626)
        id.startsWith("producemutationboost") || id.startsWith("snowycropmutationboost") ||
            id.startsWith("dawnboost") || id.startsWith("ambermoonboost") -> Color(0xFF8C0F46)
        id.startsWith("petmutationboost") -> Color(0xFFA03264)
        id.startsWith("doubleharvest") -> Color(0xFF0078B4)
        id.startsWith("doublehatch") -> Color(0xFF3C5AB4)
        id.startsWith("produceeater") -> Color(0xFFFF4500)
        id.startsWith("producerefund") -> Color(0xFFFF6347)
        id.startsWith("petrefund") -> Color(0xFF005078)
        id.startsWith("goldgranter") -> Color(0xFFE1C837)
        id.startsWith("rainbowgranter") -> Color(0xFF50AAAA)
        id.startsWith("raindance") -> Color(0xFF4CCCCC)
        id.startsWith("snowgranter") -> Color(0xFF90B8CC)
        id.startsWith("frostgranter") -> Color(0xFF94A0CC)
        id.startsWith("dawnlitgranter") -> Color(0xFFC47CB4)
        id.startsWith("amberlitgranter") -> Color(0xFFCC9060)
        else -> Color(0xFF646464)
    }
}

// ── Hatching animation view ──

@Composable
private fun HatchingView(
    result: EggHatchResponse,
    phase: String,
    eggRotation: Float,
    eggScale: Float,
    crackProgress: Float,
    petScale: Float,
    petAlpha: Float,
    flashAlpha: Float,
    particles: List<HatchParticle>,
    flyingPets: List<FlyingPet>,
    onReplay: () -> Unit,
    onBack: () -> Unit,
) {
    val firstPet = result.hatches.firstOrNull()
    val isSingle = result.count == 1

    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val showAnimArea = phase != "result" || isSingle

            if (showAnimArea) Spacer(modifier = Modifier.height(12.dp))

            // Animation area (hidden for multi-hatch result to avoid dead space)
            if (showAnimArea) Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Background effects
                Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f

                    // Mutation flash
                    if (flashAlpha > 0f) {
                        val flashColor = when (firstPet?.mutation) {
                            "Gold" -> GoldColor
                            "Rainbow" -> RainbowColors[(System.currentTimeMillis() / 200 % RainbowColors.size).toInt()]
                            else -> Color.White
                        }
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    flashColor.copy(alpha = flashAlpha * 0.6f),
                                    flashColor.copy(alpha = flashAlpha * 0.2f),
                                    Color.Transparent,
                                ),
                                center = Offset(centerX, centerY),
                                radius = 200f,
                            ),
                            center = Offset(centerX, centerY),
                            radius = 200f,
                        )
                    }

                    // Particles
                    particles.forEach { p ->
                        val alpha = (p.life * 0.9f).coerceIn(0f, 1f)
                        drawCircle(
                            color = p.color.copy(alpha = alpha),
                            center = Offset(centerX + p.x, centerY + p.y),
                            radius = p.size * p.life,
                        )
                    }
                }

                // Egg (visible during hatching)
                if (eggScale > 0f) {
                    AsyncImage(
                        model = result.eggSprite,
                        contentDescription = "Egg",
                        modifier = Modifier
                            .size(100.dp)
                            .graphicsLayer {
                                scaleX = eggScale
                                scaleY = eggScale
                                rotationZ = eggRotation
                            },
                    )

                    // Crack overlay
                    if (crackProgress > 0f) {
                        Canvas(
                            modifier = Modifier
                                .size(100.dp)
                                .graphicsLayer { rotationZ = eggRotation },
                        ) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val crackAlpha = (crackProgress * 0.8f).coerceIn(0f, 1f)

                            // Draw crack lines
                            val crackColor = Color.White.copy(alpha = crackAlpha)
                            val strokeWidth = 2f + crackProgress * 2f

                            // Main crack
                            drawLine(
                                crackColor, Offset(cx - 5f, cy - 20f * crackProgress),
                                Offset(cx + 3f, cy + 25f * crackProgress), strokeWidth,
                            )
                            if (crackProgress > 0.3f) {
                                drawLine(
                                    crackColor, Offset(cx + 3f, cy),
                                    Offset(cx + 15f * crackProgress, cy - 10f), strokeWidth * 0.8f,
                                )
                            }
                            if (crackProgress > 0.6f) {
                                drawLine(
                                    crackColor, Offset(cx - 5f, cy - 5f),
                                    Offset(cx - 18f * crackProgress, cy + 8f), strokeWidth * 0.7f,
                                )
                            }
                        }
                    }
                }

                // Pet (visible after egg breaks)
                if (petAlpha > 0f && firstPet != null) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            scaleX = petScale
                            scaleY = petScale
                            alpha = petAlpha
                        },
                    ) {
                        // Gold/Rainbow glow behind pet
                        if (firstPet.mutation != "Normal") {
                            MutationGlow(mutation = firstPet.mutation, size = 120)
                        }
                        AsyncImage(
                            model = firstPet.petSprite,
                            contentDescription = firstPet.pet,
                            modifier = Modifier.size(90.dp),
                        )
                    }
                }

                // Multi-hatch: flying pets exploding outward
                flyingPets.forEach { fp ->
                    if (fp.alpha > 0f) {
                        val petSizeDp = (50 * fp.targetScale).dp
                        AsyncImage(
                            model = fp.sprite,
                            contentDescription = null,
                            modifier = Modifier
                                .size(petSizeDp)
                                .graphicsLayer {
                                    translationX = fp.x * 8f
                                    translationY = fp.y * 8f
                                    scaleX = fp.scale
                                    scaleY = fp.scale
                                    alpha = fp.alpha
                                },
                        )
                    }
                }
            } // end animation Box

            // Status text during animation
            when (phase) {
                "hatching" -> {
                    Text("Hatching...", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = StatusConnecting)
                }
                "revealing" -> {
                    if (firstPet != null) {
                        val mutLabel = if (firstPet.mutation != "Normal") "${firstPet.mutation} " else ""
                        if (firstPet.mutation == "Rainbow") {
                            RainbowText("$mutLabel${firstPet.pet}!", fontSize = 20.sp)
                        } else {
                            Text(
                                "$mutLabel${firstPet.pet}!",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (firstPet.mutation) {
                                    "Gold" -> GoldColor
                                    else -> rarityColor(firstPet.petRarity)
                                },
                            )
                        }
                    }
                }
                "result" -> {
                    // Show full results
                    if (isSingle && firstPet != null) {
                        SinglePetResult(pet = firstPet)
                    } else {
                        MultiHatchResults(result = result)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // Summary card + action buttons at the bottom for all hatches
    if (phase == "result") {
        Spacer(modifier = Modifier.height(8.dp))
        AppCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Bet", fontSize = 12.sp, color = TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            numberFormat.format(result.totalBet),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Payout", fontSize = 12.sp, color = TextSecondary)
                    val payoutColor = if (result.totalPayout > result.totalBet) StatusConnected else StatusError
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            numberFormat.format(result.totalPayout),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = payoutColor,
                        )
                    }
                }
                val net = result.totalPayout - result.totalBet
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Net", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        "${if (net >= 0) "+" else ""}${numberFormat.format(net)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (net >= 0) StatusConnected else StatusError,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceBorder)
                            .clickable { onBack() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Back", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Accent)
                            .clickable { onReplay() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Hatch Again", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SurfaceDark)
                    }
                }
            }
        }
    }
}

// ── Single pet detailed result ──

@Composable
private fun SinglePetResult(pet: EggHatchPet) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Mutation badge
        if (pet.mutation != "Normal") {
            if (pet.mutation == "Rainbow") {
                RainbowBadgeLabel("Rainbow x${pet.mutationMultiplier}")
            } else {
                Text(
                    "Gold x${pet.mutationMultiplier}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(GoldColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Pet name + rarity
        if (pet.mutation == "Rainbow") {
            RainbowText(pet.pet, fontSize = 20.sp)
        } else {
            Text(
                pet.pet,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = when (pet.mutation) {
                    "Gold" -> GoldColor
                    else -> rarityColor(pet.petRarity)
                },
            )
        }
        Text(pet.petRarity, fontSize = 12.sp, color = rarityColor(pet.petRarity))

        Spacer(modifier = Modifier.height(10.dp))

        // Abilities
        if (pet.abilities.isNotEmpty() || pet.bonusAbilities.isNotEmpty()) {
            Text("Abilities", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            pet.abilities.forEach { ability ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(abilityColor(ability.key)),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(ability.name, fontSize = 12.sp, color = TextPrimary)
                }
            }
            pet.bonusAbilities.forEach { ability ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(abilityColor(ability.key)),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        ability.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pet.mutation == "Gold") GoldColor else RainbowColors[4],
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // STR bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("STR", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Text(
                "${pet.baseSTR} / ${pet.maxSTR}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = if (pet.maxSTR >= 95) StatusConnected else TextPrimary,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SurfaceBorder),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pet.maxSTR / 100f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            pet.maxSTR >= 95 -> StatusConnected
                            pet.maxSTR >= 85 -> Accent
                            else -> TextMuted
                        },
                    ),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Payout
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Payout: ", fontSize = 12.sp, color = TextSecondary)
            AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                numberFormat.format(pet.payout),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary,
            )
        }
    }
}

// ── Multi-hatch results grid (compact pet tiles) ──

@Composable
private fun MultiHatchResults(result: EggHatchResponse) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${result.count} Hatched",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))

        // Lazy grid — handles 100+ pets without perf issues
        val rowCount = (result.hatches.size + 2) / 3
        val gridHeight = (rowCount * 116 + (rowCount - 1) * 6).dp // 110dp tile + 6dp spacing

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(result.hatches) { pet ->
                HatchedPetTile(pet = pet)
            }
        }
    }
}

/** Compact pet tile matching the PickerPetTile style from pet section. */
@Composable
private fun HatchedPetTile(pet: EggHatchPet) {
    val borderColor = when (pet.mutation) {
        "Gold" -> GoldColor.copy(alpha = 0.5f)
        "Rainbow" -> RainbowColors[4].copy(alpha = 0.5f)
        else -> rarityColor(pet.petRarity).copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .background(SurfaceDark),
    ) {
        // Mutation sprite top-left (same as pet picker)
        if (pet.mutation != "Normal") {
            SpriteImage(
                url = mutationSpriteUrl(pet.mutation),
                size = 12.dp,
                contentDescription = pet.mutation,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
            )
        }

        // STR top-right
        Text(
            "${pet.baseSTR}/${pet.maxSTR}",
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = Accent,
            lineHeight = 9.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
        )

        // Center: sprite + name + ability dots
        Column(
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = pet.petSprite,
                contentDescription = pet.pet,
                modifier = Modifier.size(28.dp),
            )
            Text(
                pet.pet,
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 10.sp,
            )
            if (pet.abilities.isNotEmpty() || pet.bonusAbilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    pet.abilities.forEach { ability ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(abilityColor(ability.key)),
                        )
                    }
                    pet.bonusAbilities.forEach { ability ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(abilityColor(ability.key)),
                        )
                    }
                }
            }
        }

        // Payout bottom-center
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(9.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                formatCompact(pet.payout),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary,
            )
        }
    }
}

// ── Rainbow badge composable ──

@Composable
private fun RainbowBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "rainbowBadge")
    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = RainbowColors.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rainbowShift",
    )
    val idx = colorShift.toInt() % RainbowColors.size
    val frac = colorShift - colorShift.toInt()
    val currentColor = androidx.compose.ui.graphics.lerp(
        RainbowColors[idx],
        RainbowColors[(idx + 1) % RainbowColors.size],
        frac,
    )
    Text(
        "Rainbow",
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = currentColor,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(currentColor.copy(alpha = 0.15f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** Animated rainbow-colored text that cycles through all rainbow colors. */
@Composable
private fun RainbowText(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    val infiniteTransition = rememberInfiniteTransition(label = "rainbowText")
    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = RainbowColors.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rainbowTextShift",
    )
    val idx = colorShift.toInt() % RainbowColors.size
    val frac = colorShift - colorShift.toInt()
    val currentColor = androidx.compose.ui.graphics.lerp(
        RainbowColors[idx],
        RainbowColors[(idx + 1) % RainbowColors.size],
        frac,
    )
    Text(
        text,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        color = currentColor,
    )
}

/** Animated rainbow badge label with background. */
@Composable
private fun RainbowBadgeLabel(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "rainbowLabel")
    val colorShift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = RainbowColors.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rainbowLabelShift",
    )
    val idx = colorShift.toInt() % RainbowColors.size
    val frac = colorShift - colorShift.toInt()
    val currentColor = androidx.compose.ui.graphics.lerp(
        RainbowColors[idx],
        RainbowColors[(idx + 1) % RainbowColors.size],
        frac,
    )
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = currentColor,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(currentColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ── Mutation glow effect ──

@Composable
private fun MutationGlow(mutation: String, size: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "mutGlow")

    if (mutation == "Gold") {
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 0.6f,
            animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutCubic)),
            label = "goldPulse",
        )
        Canvas(modifier = Modifier.size(size.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GoldColor.copy(alpha = pulseAlpha),
                        GoldDark.copy(alpha = pulseAlpha * 0.3f),
                        Color.Transparent,
                    ),
                ),
                radius = this.size.minDimension / 2f,
            )
        }
    } else if (mutation == "Rainbow") {
        val rotationAngle by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
            label = "rainbowRot",
        )
        Canvas(modifier = Modifier.size(size.dp)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = this.size.minDimension / 2f
            RainbowColors.forEachIndexed { i, color ->
                val angle = Math.toRadians((rotationAngle + i * 60f).toDouble())
                val px = cx + cos(angle).toFloat() * radius * 0.4f
                val py = cy + sin(angle).toFloat() * radius * 0.4f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(px, py),
                        radius = radius * 0.6f,
                    ),
                    center = Offset(px, py),
                    radius = radius * 0.6f,
                )
            }
        }
    }
}
