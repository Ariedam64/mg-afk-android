package com.mgafk.app.ui.screens.pets

import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.AppTheme
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.abilityBrush
import com.mgafk.app.data.websocket.Constants
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations

internal val TILE_MIN = 58.dp
internal val GAP = 6.dp



// ── STR calculation (ported from Gemini petCalcul.ts) ──

private const val SEC_PER_HOUR = 3600
private const val XP_STRENGTH_MAX = 30
private const val BASE_STRENGTH_FLOOR = 30

internal fun calculatePetMaxStrength(species: String, targetScale: Double): Int {
    val entry = MgApi.findPet(species) ?: return 0
    val maxScale = entry.maxScale?.let { if (it > 1.0) it else 1.0 } ?: 1.0
    val ratio = if (maxScale > 1.0) (targetScale - 1.0) / (maxScale - 1.0) else 0.0
    val raw = ratio * 20.0 + 80.0
    return if (raw.isFinite()) raw.toInt().coerceAtLeast(0) else 0
}

internal fun calculatePetStrength(species: String, xp: Double, targetScale: Double): Int {
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
    /** Hunger Potions sitting in the inventory, usable straight away. */
    potionsInInventory: Int = 0,
    /** Hunger Potions sitting in the Tool Shack, retrieved on demand before use. */
    potionsInShack: Int = 0,
    /** XP Potions sitting in the inventory, usable straight away. */
    xpPotionsInInventory: Int = 0,
    /** XP Potions sitting in the Tool Shack, retrieved on demand before use. */
    xpPotionsInShack: Int = 0,
    /** Flat strength a Strength crystal is granting these pets right now, 0 when none is up. */
    strengthBonus: Int = 0,
    onFeedPet: (petItemId: String, cropItemIds: List<String>) -> Unit = { _, _ -> },
    onUsePotionOnPet: (petItemId: String) -> Unit = {},
    onUseXpPotionOnPet: (petItemId: String) -> Unit = {},
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
                    .clip(AppTheme.shapes.smallShape)
                    .background(Accent.copy(alpha = 0.1f))
                    .border(1.dp, Accent.copy(alpha = 0.25f), AppTheme.shapes.smallShape)
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
                    potionsInInventory = potionsInInventory,
                    potionsInShack = potionsInShack,
                    xpPotionsInInventory = xpPotionsInInventory,
                    xpPotionsInShack = xpPotionsInShack,
                    strengthBonus = strengthBonus,
                    onFeedPet = onFeedPet,
                    onUsePotionOnPet = onUsePotionOnPet,
                    onUseXpPotionOnPet = onUseXpPotionOnPet,
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
    potionsInInventory: Int,
    potionsInShack: Int,
    xpPotionsInInventory: Int,
    xpPotionsInShack: Int,
    strengthBonus: Int,
    onFeedPet: (petItemId: String, cropItemIds: List<String>) -> Unit,
    onUsePotionOnPet: (petItemId: String) -> Unit,
    onUseXpPotionOnPet: (petItemId: String) -> Unit,
    onSwapPet: (activePetId: String, targetPetId: String, targetIsInHutch: Boolean) -> Unit,
    onUnequipPet: (petId: String) -> Unit,
) {
    val maxHunger = Constants.maxHungerFor(pet.species) ?: 1000
    val hungerPercent = ((pet.hunger.toFloat() / maxHunger) * 100).coerceIn(0f, 100f)
    val hungerColor = when {
        hungerPercent < 5f -> StatusError
        hungerPercent < 25f -> StatusConnecting
        else -> StatusConnected
    }

    val strength = remember(pet.species, pet.xp, pet.targetScale, apiReady) {
        calculatePetStrength(pet.species, pet.xp, pet.targetScale)
    }
    val maxStrength = remember(pet.species, pet.targetScale, apiReady) {
        calculatePetMaxStrength(pet.species, pet.targetScale)
    }
    // "Fully grown" in the game's words: the point where it refuses an XP Potion outright.
    val isMaxStrength = maxStrength > 0 && strength >= maxStrength
    var showFeedPicker by remember { mutableStateOf(false) }
    var showSwapPicker by remember { mutableStateOf(false) }
    var showXpPotionDialog by remember { mutableStateOf(false) }

    val chipShape = RoundedCornerShape(6.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.smallShape)
            .border(1.dp, Color.White.copy(alpha = if (isSelected) 0.25f else 0.12f), AppTheme.shapes.smallShape)
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
                    // A crystal's bonus is shown apart from the pet's own strength: it is not
                    // progress, it lasts as long as the crystal does, and it reads above the
                    // ceiling on purpose.
                    val strText = when {
                        strengthBonus > 0 -> "STR ${strength + strengthBonus} (+$strengthBonus)"
                        isMaxStrength -> "STR $strength"
                        else -> "STR $strength/$maxStrength"
                    }
                    val strColor = if (isMaxStrength || strengthBonus > 0) StatusConnecting else Accent
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
                        val bg = remember(abilityId, apiReady) { abilityBrush(abilityId) }
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

        // ── Right: action column (Feed + Swap + Remove) - slides in on select ──
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
                // Offered only when there is a potion to spend. A fully grown pet keeps the
                // button visible but inert, so it reads as "nothing left to gain" rather than
                // as a missing feature.
                if (xpPotionsInInventory + xpPotionsInShack > 0) {
                    Text(
                        text = "XP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMaxStrength) TextMuted.copy(alpha = 0.5f) else btnColor,
                        textAlign = TextAlign.Center,
                        modifier = if (isMaxStrength) btnMod
                        else btnMod.then(Modifier.clickable { showXpPotionDialog = true }),
                    )
                }
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
            potionsInInventory = potionsInInventory,
            potionsInShack = potionsInShack,
            onConfirm = { selectedIds ->
                showFeedPicker = false
                if (selectedIds.isNotEmpty()) onFeedPet(pet.id, selectedIds)
            },
            onUsePotion = {
                showFeedPicker = false
                onUsePotionOnPet(pet.id)
            },
            onDismiss = { showFeedPicker = false },
        )
    }

    if (showXpPotionDialog) {
        XpPotionDialog(
            pet = pet,
            strength = strength,
            maxStrength = maxStrength,
            apiReady = apiReady,
            inInventory = xpPotionsInInventory,
            inShack = xpPotionsInShack,
            onConfirm = {
                showXpPotionDialog = false
                onUseXpPotionOnPet(pet.id)
            },
            onDismiss = { showXpPotionDialog = false },
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
            .clip(AppTheme.shapes.smallShape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), AppTheme.shapes.smallShape)
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
