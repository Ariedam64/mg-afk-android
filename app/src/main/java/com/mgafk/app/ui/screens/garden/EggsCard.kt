package com.mgafk.app.ui.screens.garden

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.model.GardenEggSnapshot
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.theme.rarityBorder
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Rarity colors
private val RarityCommon = Color(0xFFE7E7E7)
private val RarityUncommon = Color(0xFF67BD4D)
private val RarityRare = Color(0xFF0071C6)
private val RarityLegendary = Color(0xFFFFC734)
private val RarityMythical = Color(0xFF9944A7)
private val RarityDivine = Color(0xFFFF7835)
private val RarityCelestial = Color(0xFFFF00FF)

private fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "common" -> RarityCommon
    "uncommon" -> RarityUncommon
    "rare" -> RarityRare
    "legendary" -> RarityLegendary
    "mythical", "mythic" -> RarityMythical
    "divine" -> RarityDivine
    "celestial" -> RarityCelestial
    else -> TextMuted
}

private val TILE_MIN_WIDTH = 76.dp
private val TILE_SPACING = 6.dp

// STR helpers (same as InventoryCard)
private const val XP_PER_HOUR = 3600.0
private const val BASE_STR = 80
private const val MAX_STR = 100
private const val STR_GAINED = 30

private fun maxStr(species: String, scale: Double): Int {
    val ms = MgApi.findPet(species)?.maxScale ?: return BASE_STR
    if (scale <= 1.0) return BASE_STR
    if (scale >= ms) return MAX_STR
    return (BASE_STR + 20 * (scale - 1.0) / (ms - 1.0)).toInt()
}

private fun curStr(species: String, xp: Double, max: Int): Int {
    val htm = MgApi.findPet(species)?.hoursToMature ?: return max - STR_GAINED
    val gained = minOf(STR_GAINED / htm * (xp / XP_PER_HOUR), STR_GAINED.toDouble())
    return ((max - STR_GAINED) + gained).toInt()
}

private fun formatTimeRemaining(endTime: Long, now: Long): String {
    val remaining = (endTime - now).coerceAtLeast(0)
    if (remaining <= 0) return ""
    val totalSec = remaining / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/** Parse ability color — returns a Brush (gradient or solid). Same as PetHungerCard. */
private fun parseAbilityBrush(raw: String?): Brush {
    if (raw == null) return SolidColor(Color(0xFF646464))
    val hexPattern = Regex("#[0-9A-Fa-f]{6}")
    val hexColors = hexPattern.findAll(raw).mapNotNull { match ->
        try { Color(android.graphics.Color.parseColor(match.value)) } catch (_: Exception) { null }
    }.toList()
    if (hexColors.size >= 2 && raw.contains("gradient", ignoreCase = true)) {
        return Brush.linearGradient(hexColors)
    }
    if (hexColors.isNotEmpty()) return SolidColor(hexColors.first())
    return try { SolidColor(Color(android.graphics.Color.parseColor(raw))) } catch (_: Exception) { SolidColor(Color(0xFF646464)) }
}

@Composable
fun EggsCard(
    eggs: List<GardenEggSnapshot>,
    apiReady: Boolean = false,
    onHatch: (slot: Int) -> Unit = {},
    lastHatchedPet: InventoryPetItem? = null,
    lastHatchedEggId: String = "",
    onDismissHatchedPet: () -> Unit = {},
) {
    var selectedEggTileId by remember { mutableStateOf<Int?>(null) }

    // Tick every second for live countdowns
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    AppCard(
        title = "Eggs",
        trailing = {
            Text(
                text = "${eggs.size} eggs",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Accent.copy(alpha = 0.7f),
            )
        },
        collapsible = true,
        persistKey = "garden.eggs",
    ) {
        if (eggs.isEmpty()) {
            Text("No eggs in the garden.", fontSize = 12.sp, color = TextMuted)
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = ((maxWidth + TILE_SPACING) / (TILE_MIN_WIDTH + TILE_SPACING))
                    .toInt().coerceAtLeast(1)
                val rows = eggs.chunked(columns)

                Column(verticalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                    rows.forEach { rowEggs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                        ) {
                            rowEggs.forEach { egg ->
                                Box(modifier = Modifier.weight(1f).clickable {
                                    selectedEggTileId = egg.tileId
                                }) {
                                    EggTile(egg = egg, apiReady = apiReady, now = now)
                                }
                            }
                            repeat(columns - rowEggs.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Egg detail dialog — live lookup
    selectedEggTileId?.let { tileId ->
        val liveEgg = eggs.find { it.tileId == tileId }
        if (liveEgg != null) {
            EggDetailDialog(
                egg = liveEgg,
                apiReady = apiReady,
                now = now,
                onHatch = {
                    onHatch(tileId)
                    selectedEggTileId = null
                },
                onDismiss = { selectedEggTileId = null },
            )
        } else {
            // Egg was hatched/removed — close dialog
            selectedEggTileId = null
        }
    }

    // Hatched pet popup with animation
    if (lastHatchedPet != null) {
        HatchedPetDialog(
            pet = lastHatchedPet,
            eggId = lastHatchedEggId,
            apiReady = apiReady,
            onDismiss = onDismissHatchedPet,
        )
    }
}

// ── Egg tile ──

@Composable
private fun EggTile(egg: GardenEggSnapshot, apiReady: Boolean, now: Long) {
    val entry = remember(egg.eggId, apiReady) { MgApi.findItem(egg.eggId) }
    val displayName = entry?.name ?: egg.eggId
    val spriteUrl = entry?.sprite
    val color = rarityColor(entry?.rarity)

    val totalMs = (egg.maturedAt - egg.plantedAt).coerceAtLeast(1)
    val elapsedMs = (now - egg.plantedAt).coerceAtLeast(0)
    val fraction = (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val isHatched = elapsedMs >= totalMs

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(10.dp))
            .rarityBorder(rarity = entry?.rarity, width = 1.5.dp, shape = RoundedCornerShape(10.dp), alpha = 0.5f)
            .background(SurfaceDark)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SpriteImage(url = spriteUrl, size = 28.dp, contentDescription = displayName)

        Text(
            text = displayName,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 10.sp,
        )

        // Progress bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .background(
                            if (isHatched) StatusConnected.copy(alpha = 0.8f)
                            else color.copy(alpha = 0.8f)
                        ),
                )
            }
            Spacer(modifier = Modifier.size(3.dp))
            Text(
                text = "${(fraction * 100).toInt()}%",
                fontSize = 7.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
                lineHeight = 8.sp,
            )
        }

        // Time remaining or Ready
        val timeText = if (isHatched) "Ready!" else formatTimeRemaining(egg.maturedAt, now)
        Text(
            text = timeText,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHatched) StatusConnected else Accent.copy(alpha = 0.8f),
            lineHeight = 10.sp,
        )
    }
}

// ── Egg detail dialog ──

@Composable
private fun EggDetailDialog(
    egg: GardenEggSnapshot,
    apiReady: Boolean,
    now: Long,
    onHatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(egg.eggId, apiReady) { MgApi.findItem(egg.eggId) }
    val displayName = entry?.name ?: egg.eggId
    val spriteUrl = entry?.sprite
    val color = rarityColor(entry?.rarity)

    val totalMs = (egg.maturedAt - egg.plantedAt).coerceAtLeast(1)
    val elapsedMs = (now - egg.plantedAt).coerceAtLeast(0)
    val fraction = (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val isMature = elapsedMs >= totalMs

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpriteImage(url = spriteUrl, size = 56.dp, contentDescription = displayName)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            if (entry?.rarity != null) {
                Text(
                    entry.rarity,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Progress", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        if (isMature) "Ready!" else "${(fraction * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMature) StatusConnected else TextPrimary,
                    )
                }

                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.15f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .background(
                                if (isMature) StatusConnected.copy(alpha = 0.8f)
                                else color.copy(alpha = 0.8f)
                            ),
                    )
                }

                // Time remaining
                if (!isMature) {
                    val remaining = formatTimeRemaining(egg.maturedAt, now)
                    if (remaining.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Time left", fontSize = 12.sp, color = TextSecondary)
                            Text(remaining, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                }

                // Tile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Tile", fontSize = 12.sp, color = TextSecondary)
                    Text("#${egg.tileId}", fontSize = 12.sp, color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hatch button
            Button(
                onClick = onHatch,
                enabled = isMature,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B5CF6),
                    disabledContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    if (isMature) "Hatch" else "Not ready",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMature) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ── Hatched pet dialog with egg crack animation ──

// Animation phases: 0=egg shake, 1=egg explode + flash, 2=pet reveal, 3=info slide in
private const val PHASE_EGG_SHAKE = 0
private const val PHASE_EGG_EXPLODE = 1
private const val PHASE_PET_REVEAL = 2
private const val PHASE_INFO = 3

@Composable
private fun HatchedPetDialog(
    pet: InventoryPetItem,
    eggId: String,
    apiReady: Boolean,
    onDismiss: () -> Unit,
) {
    val petEntry = remember(pet.petSpecies, apiReady) { MgApi.findPet(pet.petSpecies) }
    val eggEntry = remember(eggId, apiReady) { MgApi.findItem(eggId) }
    val name = pet.name?.ifBlank { null } ?: petEntry?.name ?: pet.petSpecies
    val color = rarityColor(petEntry?.rarity)
    val ms = maxStr(pet.petSpecies, pet.targetScale)
    val cs = curStr(pet.petSpecies, pet.xp, ms)

    // Animation state
    var phase by remember { mutableIntStateOf(PHASE_EGG_SHAKE) }
    val eggScale = remember { Animatable(1f) }
    val eggAlpha = remember { Animatable(1f) }
    val eggShakeX = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }
    val petScale = remember { Animatable(0f) }
    val petAlpha = remember { Animatable(0f) }
    val infoAlpha = remember { Animatable(0f) }
    val infoOffsetY = remember { Animatable(30f) }

    // Run the animation sequence
    LaunchedEffect(Unit) {
        // Phase 0: Egg shake (3 shakes getting bigger)
        phase = PHASE_EGG_SHAKE
        repeat(3) { i ->
            val amplitude = 6f + i * 4f
            eggShakeX.animateTo(amplitude, tween(60))
            eggShakeX.animateTo(-amplitude, tween(60))
        }
        eggShakeX.animateTo(0f, tween(40))
        eggScale.animateTo(1.15f, tween(150))
        delay(100)

        // Phase 1: Egg explode + flash
        phase = PHASE_EGG_EXPLODE
        coroutineScope {
            launch { eggScale.animateTo(2.5f, tween(300)) }
            launch { eggAlpha.animateTo(0f, tween(300)) }
            launch {
                flashAlpha.animateTo(0.9f, tween(150))
                flashAlpha.animateTo(0f, tween(250))
            }
        }
        delay(100)

        // Phase 2: Pet reveal with bounce
        phase = PHASE_PET_REVEAL
        coroutineScope {
            launch {
                petScale.animateTo(1.12f, tween(250))
                petScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            launch { petAlpha.animateTo(1f, tween(200)) }
        }
        delay(200)

        // Phase 3: Info slide in
        phase = PHASE_INFO
        coroutineScope {
            launch { infoAlpha.animateTo(1f, tween(300)) }
            launch { infoOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
        }
    }

    Dialog(onDismissRequest = { if (phase >= PHASE_INFO) onDismiss() }) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard),
            contentAlignment = Alignment.Center,
        ) {
            // White flash overlay
            if (flashAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(flashAlpha.value)
                        .background(Color.White),
                )
            }

            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Sprite area — egg or pet
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Egg sprite (fading out)
                    if (eggAlpha.value > 0f) {
                        SpriteImage(
                            url = eggEntry?.sprite,
                            size = 64.dp,
                            contentDescription = "egg",
                            modifier = Modifier
                                .scale(eggScale.value)
                                .alpha(eggAlpha.value)
                                .graphicsLayer { translationX = eggShakeX.value },
                        )
                    }

                    // Pet sprite (fading in)
                    if (petAlpha.value > 0f) {
                        SpriteImage(
                            category = "pets",
                            name = pet.petSpecies,
                            size = 64.dp,
                            contentDescription = pet.petSpecies,
                            mutations = pet.mutations,
                            modifier = Modifier
                                .scale(petScale.value)
                                .alpha(petAlpha.value),
                        )
                    }
                }

                // Everything below slides in
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(infoAlpha.value)
                        .graphicsLayer { translationY = infoOffsetY.value },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        "Egg Hatched!",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusConnected,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )

                    if (petEntry?.rarity != null) {
                        Text(
                            petEntry.rarity,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Details
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // STR
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("STR", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                "$cs/$ms",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cs >= ms) Color(0xFFFBBF24) else Accent,
                            )
                        }

                        // Mutations
                        if (pet.mutations.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Mutations", fontSize = 12.sp, color = TextSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    sortMutations(pet.mutations).forEach { mutation ->
                                        SpriteImage(url = mutationSpriteUrl(mutation), size = 16.dp, contentDescription = mutation)
                                    }
                                }
                            }
                        }

                        // Abilities chips
                        if (pet.abilities.isNotEmpty()) {
                            Text("Abilities", fontSize = 12.sp, color = TextSecondary)
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                pet.abilities.forEach { abilityId ->
                                    val abilityEntry = remember(abilityId, apiReady) { MgApi.getAbilities()[abilityId] }
                                    val displayName = abilityEntry?.name ?: abilityId
                                    val bg = remember(abilityEntry?.color) { parseAbilityBrush(abilityEntry?.color) }
                                    Text(
                                        displayName,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(bg, alpha = 0.85f)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusConnected),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            "Nice!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
