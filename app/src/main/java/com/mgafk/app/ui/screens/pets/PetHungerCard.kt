package com.mgafk.app.ui.screens.pets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.data.websocket.Constants
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.theme.rarityBorder
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations

private val TILE_MIN = 58.dp
private val GAP = 6.dp

private val RarityCommon = Color(0xFFE7E7E7)
private val RarityUncommon = Color(0xFF67BD4D)
private val RarityRare = Color(0xFF0071C6)
private val RarityLegendary = Color(0xFFFFC734)
private val RarityMythical = Color(0xFF9944A7)
private val RarityDivine = Color(0xFFFF7835)
private val RarityCelestial = Color(0xFFFF00FF)

/** Parse ability color — returns a Brush (gradient or solid). */
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

private fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "common" -> RarityCommon; "uncommon" -> RarityUncommon; "rare" -> RarityRare
    "legendary" -> RarityLegendary; "mythical", "mythic" -> RarityMythical
    "divine" -> RarityDivine; "celestial" -> RarityCelestial; else -> TextMuted
}

// ── STR calculation (ported from Gemini petCalcul.ts) ──

private const val SEC_PER_HOUR = 3600
private const val XP_STRENGTH_MAX = 30
private const val BASE_STRENGTH_FLOOR = 30

private fun calculatePetMaxStrength(species: String, targetScale: Double): Int {
    val entry = MgApi.findPet(species) ?: return 0
    val maxScale = entry.maxScale?.let { if (it > 1.0) it else 1.0 } ?: 1.0
    val ratio = if (maxScale > 1.0) (targetScale - 1.0) / (maxScale - 1.0) else 0.0
    val raw = ratio * 20.0 + 80.0
    return if (raw.isFinite()) raw.toInt().coerceAtLeast(0) else 0
}

private fun calculatePetStrength(species: String, xp: Double, targetScale: Double): Int {
    val entry = MgApi.findPet(species) ?: return 0
    val hoursToMature = entry.hoursToMature?.let { if (it > 0.0) it else 1.0 } ?: 1.0

    val maxStrength = calculatePetMaxStrength(species, targetScale)
    if (maxStrength <= 0) return 0

    val xpRate = xp.coerceAtLeast(0.0) / (hoursToMature * SEC_PER_HOUR)
    val xpComponent = (xpRate * XP_STRENGTH_MAX).toInt().coerceAtMost(XP_STRENGTH_MAX)
    val baseStrength = (maxStrength - BASE_STRENGTH_FLOOR).coerceAtLeast(0)

    return (baseStrength + xpComponent).coerceIn(0, maxStrength)
}

// ── Ability color mapping (matches Gemini UI) ──

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
        id.startsWith("copycat") -> Color(0xFFFF8C00)
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

private const val MAX_PET_SLOTS = 3

/** Combined pet from inventory + hutch for the swap picker. */
data class SwapCandidate(
    val pet: InventoryPetItem,
    val isInHutch: Boolean,
)

@Composable
fun ActivePetsCard(
    pets: List<PetSnapshot>,
    produce: List<InventoryProduceItem> = emptyList(),
    inventoryPets: List<InventoryPetItem> = emptyList(),
    hutchPets: List<InventoryPetItem> = emptyList(),
    apiReady: Boolean = false,
    showTip: Boolean = false,
    onDismissTip: () -> Unit = {},
    onFeedPet: (petItemId: String, cropItemIds: List<String>) -> Unit = { _, _ -> },
    onSwapPet: (activePetId: String, targetPetId: String, targetIsInHutch: Boolean) -> Unit = { _, _, _ -> },
    onEquipPet: (targetPetId: String, targetIsInHutch: Boolean) -> Unit = { _, _ -> },
    onUnequipPet: (petId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Combine inventory + hutch pets for swap/equip picker, excluding already active pets
    val activePetIds = remember(pets) { pets.map { it.id }.toSet() }
    val swapCandidates = remember(inventoryPets, hutchPets, activePetIds) {
        val inv = inventoryPets.filter { it.id !in activePetIds }.map { SwapCandidate(it, false) }
        val hutch = hutchPets.filter { it.id !in activePetIds }.map { SwapCandidate(it, true) }
        (inv + hutch).sortedBy { it.pet.petSpecies }
    }

    var selectedPetId by remember { mutableStateOf<String?>(null) }

    AppCard(modifier = modifier, title = "Active Pets", collapsible = true, persistKey = "pets.active") {
        AnimatedVisibility(visible = showTip, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Accent.copy(alpha = 0.1f))
                    .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .clickable { onDismissTip() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tap a pet to feed, swap or remove it.",
                            fontSize = 11.sp,
                            color = Accent,
                            lineHeight = 15.sp,
                        )
                    }
                    Text(
                        text = "OK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        modifier = Modifier.clickable { onDismissTip() },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pets.forEach { pet ->
                ActivePetRow(
                    pet = pet,
                    produce = produce,
                    candidates = swapCandidates,
                    apiReady = apiReady,
                    isSelected = selectedPetId == pet.id,
                    onSelect = { selectedPetId = if (selectedPetId == pet.id) null else pet.id },
                    onFeedPet = onFeedPet,
                    onSwapPet = onSwapPet,
                    onUnequipPet = onUnequipPet,
                )
            }
            // Empty slot placeholders (+ button)
            val emptySlots = MAX_PET_SLOTS - pets.size
            if (emptySlots > 0) {
                repeat(emptySlots) {
                    EmptyPetSlot(candidates = swapCandidates, apiReady = apiReady, onEquipPet = onEquipPet)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivePetRow(
    pet: PetSnapshot,
    produce: List<InventoryProduceItem>,
    candidates: List<SwapCandidate>,
    apiReady: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onFeedPet: (petItemId: String, cropItemIds: List<String>) -> Unit,
    onSwapPet: (activePetId: String, targetPetId: String, targetIsInHutch: Boolean) -> Unit,
    onUnequipPet: (petId: String) -> Unit,
) {
    val maxHunger = Constants.PET_HUNGER_COSTS[pet.species.lowercase()] ?: 1000
    val hungerPercent = ((pet.hunger.toFloat() / maxHunger) * 100).coerceIn(0f, 100f)
    val hungerColor = when {
        hungerPercent < 5f -> StatusError
        hungerPercent < 25f -> Color(0xFFFBBF24)
        else -> StatusConnected
    }

    val strength = remember(pet.species, pet.xp, pet.targetScale, apiReady) {
        calculatePetStrength(pet.species, pet.xp, pet.targetScale)
    }
    val maxStrength = remember(pet.species, pet.targetScale, apiReady) {
        calculatePetMaxStrength(pet.species, pet.targetScale)
    }
    var showFeedPicker by remember { mutableStateOf(false) }
    var showSwapPicker by remember { mutableStateOf(false) }

    val chipShape = RoundedCornerShape(6.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = if (isSelected) 0.25f else 0.12f), RoundedCornerShape(10.dp))
            .clickable { onSelect() }
            .background(SurfaceBorder.copy(alpha = 0.10f))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Left: pet info ──
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header: sprite + name + STR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpriteImage(category = "pets", name = pet.species, size = 36.dp, contentDescription = pet.species, mutations = pet.mutations)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    pet.name.ifBlank { pet.species },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                if (pet.mutations.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    sortMutations(pet.mutations).forEach { SpriteImage(url = mutationSpriteUrl(it), size = 14.dp, contentDescription = it) }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (apiReady && maxStrength > 0) {
                    val isMaxStr = strength >= maxStrength
                    val strText = if (isMaxStr) "STR $strength" else "STR $strength/$maxStrength"
                    val strColor = if (isMaxStr) Color(0xFFFBBF24) else Accent
                    Text(
                        strText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = strColor,
                    )
                }
            }

            // Hunger bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Hunger", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextMuted)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(hungerColor.copy(alpha = 0.15f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (hungerPercent / 100f).coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(hungerColor),
                    )
                }
                Text(
                    "%.1f%%".format(hungerPercent),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = hungerColor,
                )
            }

            // Abilities chips (centered)
            if (pet.abilities.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    pet.abilities.forEach { abilityId ->
                        val entry = remember(abilityId, apiReady) { MgApi.getAbilities()[abilityId] }
                        val displayName = entry?.name ?: abilityId
                        val bg = remember(entry?.color) {
                            parseAbilityBrush(entry?.color)
                        }
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

        // ── Right: action column (Feed + Swap + Remove) — slides in on select ──
        val btnWidth = 52.dp
        AnimatedVisibility(
            visible = isSelected,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(200),
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(150),
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.CenterVertically),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(btnWidth),
            ) {
                val btnColor = TextSecondary
                val btnMod = Modifier
                    .width(btnWidth)
                    .clip(chipShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(vertical = 6.dp)
                Text(
                    text = "Feed",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = btnColor,
                    textAlign = TextAlign.Center,
                    modifier = btnMod.then(Modifier.clickable { showFeedPicker = true }),
                )
                Text(
                    text = "Swap",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = btnColor,
                    textAlign = TextAlign.Center,
                    modifier = btnMod.then(Modifier.clickable { showSwapPicker = true }),
                )
                Text(
                    text = "Remove",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = StatusError.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(btnWidth)
                        .clip(chipShape)
                        .background(StatusError.copy(alpha = 0.06f))
                        .clickable { onUnequipPet(pet.id) }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }

    if (showFeedPicker) {
        FeedPetPickerDialog(
            pet = pet,
            produce = produce,
            apiReady = apiReady,
            onConfirm = { selectedIds ->
                showFeedPicker = false
                if (selectedIds.isNotEmpty()) onFeedPet(pet.id, selectedIds)
            },
            onDismiss = { showFeedPicker = false },
        )
    }

    if (showSwapPicker) {
        PetPickerDialog(
            title = "Swap ${pet.name.ifBlank { pet.species }}",
            candidates = candidates,
            apiReady = apiReady,
            onSelect = { candidate ->
                showSwapPicker = false
                onSwapPet(pet.id, candidate.pet.id, candidate.isInHutch)
            },
            onDismiss = { showSwapPicker = false },
        )
    }
}

// ── Empty slot placeholder (+) ──

@Composable
private fun EmptyPetSlot(
    candidates: List<SwapCandidate>,
    apiReady: Boolean,
    onEquipPet: (targetPetId: String, targetIsInHutch: Boolean) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .background(SurfaceBorder.copy(alpha = 0.06f))
            .clickable { showPicker = true },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
        )
    }

    if (showPicker) {
        PetPickerDialog(
            title = "Equip Pet",
            candidates = candidates,
            apiReady = apiReady,
            onSelect = { candidate ->
                showPicker = false
                onEquipPet(candidate.pet.id, candidate.isInHutch)
            },
            onDismiss = { showPicker = false },
        )
    }
}

// ── Pet picker dialog (used for both Swap and Equip) ──

@Composable
private fun PetPickerDialog(
    title: String,
    candidates: List<SwapCandidate>,
    apiReady: Boolean,
    onSelect: (SwapCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(16.dp),
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${candidates.size} pets available", fontSize = 11.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(12.dp))

            if (candidates.isEmpty()) {
                Text(
                    "No pets available in inventory or hutch.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(TILE_MIN),
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                    verticalArrangement = Arrangement.spacedBy(GAP),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 100.dp)
                        .height(320.dp),
                ) {
                    items(candidates, key = { it.pet.id }) { candidate ->
                        PetCandidateTile(
                            candidate = candidate,
                            apiReady = apiReady,
                            onClick = { onSelect(candidate) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                ) {
                    Text("Cancel", fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun PetCandidateTile(
    candidate: SwapCandidate,
    apiReady: Boolean,
    onClick: () -> Unit,
) {
    val pet = candidate.pet
    val entry = remember(pet.petSpecies, apiReady) { MgApi.findPet(pet.petSpecies) }
    val name = pet.name?.ifBlank { null } ?: entry?.name ?: pet.petSpecies
    val rarity = entry?.rarity
    val str = remember(pet.petSpecies, pet.xp, pet.targetScale, apiReady) {
        calculatePetStrength(pet.petSpecies, pet.xp, pet.targetScale)
    }
    val maxStr = remember(pet.petSpecies, pet.targetScale, apiReady) {
        calculatePetMaxStrength(pet.petSpecies, pet.targetScale)
    }
    val isMaxStr = str >= maxStr && maxStr > 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .rarityBorder(rarity = rarity, width = 1.5.dp, shape = RoundedCornerShape(10.dp), alpha = 0.5f)
            .background(SurfaceDark)
            .clickable(onClick = onClick),
    ) {
        // Mutation icon top-left
        if (pet.mutations.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                sortMutations(pet.mutations).take(2).forEach { SpriteImage(url = mutationSpriteUrl(it), size = 12.dp, contentDescription = it) }
            }
        }
        // STR top-right
        if (maxStr > 0) {
            val strText = if (isMaxStr) "$str" else "$str/$maxStr"
            val strColor = if (isMaxStr) Color(0xFFFBBF24) else Accent
            Text(
                strText, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                color = strColor, lineHeight = 9.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
            )
        }
        // Center content
        Column(
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpriteImage(category = "pets", name = pet.petSpecies, size = 28.dp, contentDescription = pet.petSpecies, mutations = pet.mutations)
            Text(
                name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp,
            )
            if (pet.abilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    pet.abilities.forEach { abilityId ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(abilityColor(abilityId)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedPetPickerDialog(
    pet: PetSnapshot,
    produce: List<InventoryProduceItem>,
    apiReady: Boolean,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val petEntry = remember(pet.species, apiReady) { MgApi.findPet(pet.species) }
    val diet = remember(petEntry) { petEntry?.diet ?: emptyList() }
    val compatible = remember(produce, diet, apiReady) {
        if (diet.isEmpty()) emptyList()
        else produce.filter { it.species in diet }
    }
    var selected by remember { mutableStateOf(setOf<String>()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(16.dp),
        ) {
            Text(
                "Feed ${pet.name.ifBlank { pet.species }}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            if (diet.isNotEmpty()) {
                val dietNames = remember(diet, apiReady) {
                    diet.mapNotNull { MgApi.findItem(it)?.name?.removeSuffix(" Seed") }
                }
                Text(
                    "Diet: ${dietNames.joinToString(", ")}",
                    fontSize = 10.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Text(
                "${selected.size} selected",
                fontSize = 11.sp,
                color = if (selected.isNotEmpty()) StatusConnected else TextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )

            if (compatible.isEmpty()) {
                Text(
                    "No compatible produce in inventory.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(TILE_MIN),
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                    verticalArrangement = Arrangement.spacedBy(GAP),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 100.dp)
                        .height(320.dp),
                ) {
                    items(compatible, key = { it.id }) { item ->
                        val isSelected = item.id in selected
                        FeedProduceTile(
                            item = item,
                            apiReady = apiReady,
                            isSelected = isSelected,
                            onClick = {
                                selected = if (isSelected) selected - item.id else selected + item.id
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                ) {
                    Text("Cancel", fontSize = 12.sp, color = TextSecondary)
                }
                Button(
                    onClick = { onConfirm(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Text("Feed ${selected.size}", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FeedProduceTile(
    item: InventoryProduceItem,
    apiReady: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val color = rarityColor(entry?.rarity)
    val borderColor = if (isSelected) StatusConnected else color.copy(alpha = 0.5f)
    val borderWidth = if (isSelected) 2.5.dp else 1.5.dp
    val price = remember(item.species, item.scale, item.mutations, apiReady) {
        PriceCalculator.calculateCropSellPrice(item.species, item.scale, item.mutations)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .background(if (isSelected) StatusConnected.copy(0.1f) else SurfaceDark)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(category = "plants", name = item.species, size = 28.dp, contentDescription = name, mutations = item.mutations)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        if (price != null) {
            Text(PriceCalculator.formatPrice(price), fontSize = 8.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700), lineHeight = 10.sp)
        }
        if (item.mutations.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                sortMutations(item.mutations).take(3).forEach { SpriteImage(url = mutationSpriteUrl(it), size = 12.dp, contentDescription = it) }
            }
        }
        if (isSelected) {
            Text("\u2713", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusConnected, lineHeight = 12.sp)
        }
    }
}
