package com.mgafk.app.ui.screens.garden

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.repository.CropCleansers
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.MutationPotions
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.PlantCompositeSprite
import com.mgafk.app.ui.components.PlantSlotRender
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.rarityColor
import androidx.compose.ui.window.Dialog
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import com.mgafk.app.ui.components.formatTimeRemaining
import com.mgafk.app.ui.components.formatQuantity

// ── Multi-slot plant detail dialog ──

@Composable
internal fun MultiSlotPlantDetailDialog(
    plant: GardenEntry.MultiSlotPlant,
    wateringCans: Int,
    planterPots: Int,
    cropCleansers: List<CropCleansers.Offer>,
    onHarvest: (slot: Int, slotIndex: Int) -> Unit,
    onWater: (slot: Int) -> Unit,
    onPot: (slot: Int) -> Unit,
    onCleanse: (slot: Int, slotIndex: Int, toolId: String) -> Unit,
    onDismiss: () -> Unit,
    mutationPotions: List<MutationPotions.Offer> = emptyList(),
    onApplyPotion: (slot: Int, slotIndex: Int, potion: MutationPotions.Offer) -> Unit = { _, _, _ -> },
) {
    val color = rarityColor(plant.rarity)
    // Not keyed on the tile - see [MultiSlotPlantTile].
    val species = plant.crops.firstOrNull()?.snapshot?.species ?: ""
    val headerSlots = remember(plant.crops) {
        plant.crops.map { PlantSlotRender(it.snapshot.species, it.snapshot.mutations, MgApi.cropSizeMultiplier(it.snapshot.species, it.snapshot.size)) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(AppTheme.colors.surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            PlantCompositeSprite(species = species, slots = headerSlots, size = 80.dp, contentDescription = plant.displayName)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                plant.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
            )

            if (plant.rarity != null) {
                Text(
                    plant.rarity,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }

            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tile #${plant.tileId}",
                    fontSize = 11.sp,
                    color = AppTheme.colors.textSecondary,
                )
                Text(
                    "${plant.crops.size} slots",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.accent,
                )
                if (plant.totalValue > 0) {
                    Text(
                        PriceCalculator.formatFull(plant.totalValue),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tick every second for live countdown
            val now = rememberSecondTick()

            // Scrollable crop list - capped so the Pot button stays visible on
            // plants with many slots (e.g. 8-slot FavaBean).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                plant.crops.forEachIndexed { index, crop ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = AppTheme.colors.surfaceHigh.copy(alpha = 0.4f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    CropSlotRow(
                        crop = crop,
                        slotLabel = "Slot ${crop.snapshot.slotIndex + 1}",
                        color = color,
                        wateringCans = wateringCans,
                        cropCleansers = cropCleansers,
                        now = now,
                        onHarvest = { onHarvest(crop.snapshot.tileId, crop.snapshot.slotIndex) },
                        onWater = { onWater(crop.snapshot.tileId) },
                        onCleanse = { toolId -> onCleanse(crop.snapshot.tileId, crop.snapshot.slotIndex, toolId) },
                        mutationPotions = mutationPotions,
                        onApplyPotion = { potion ->
                            onApplyPotion(crop.snapshot.tileId, crop.snapshot.slotIndex, potion)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pot button for the whole plant
            val canPot = planterPots > 0
            val potSprite = remember { MgApi.findItem("PlanterPot")?.sprite }
            Button(
                onClick = { onPot(plant.tileId) },
                enabled = canPot,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD97706),
                    disabledContainerColor = Color(0xFFD97706).copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = AppTheme.shapes.smallShape,
            ) {
                SpriteImage(url = potSprite, size = 18.dp, contentDescription = "pot")
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    if (canPot) "Pot x${formatQuantity(planterPots)}" else "No pots",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canPot) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ── Individual crop row inside multi-slot dialog ──

@Composable
private fun CropSlotRow(
    crop: ResolvedPlant,
    slotLabel: String,
    color: Color,
    wateringCans: Int,
    cropCleansers: List<CropCleansers.Offer>,
    now: Long,
    onHarvest: () -> Unit,
    onWater: () -> Unit,
    onCleanse: (toolId: String) -> Unit,
    mutationPotions: List<MutationPotions.Offer>,
    onApplyPotion: (MutationPotions.Offer) -> Unit,
) {
    val sizePercent = crop.snapshot.size.toDouble()
    val isMature = crop.snapshot.endTime > 0 && now >= crop.snapshot.endTime
    val canWater = !isMature && wateringCans > 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpriteImage(
            category = "plants",
            name = crop.snapshot.species,
            size = 32.dp,
            contentDescription = crop.displayName,
            mutations = crop.snapshot.mutations,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        // Slot label + mutations + remaining time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(slotLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isMature) {
                    val remaining = formatTimeRemaining(crop.snapshot.endTime, System.currentTimeMillis())
                    if (remaining.isNotEmpty()) {
                        Text(remaining, fontSize = 9.sp, color = AppTheme.colors.textSecondary)
                    }
                }
                if (crop.snapshot.mutations.isNotEmpty() || crop.snapshot.preserved) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (crop.snapshot.preserved) {
                            SpriteImage(url = MgApi.preservationSpriteUrl, size = 14.dp, contentDescription = "Preserved")
                        }
                        sortMutations(crop.snapshot.mutations).forEach { mutation ->
                            SpriteImage(url = mutationSpriteUrl(mutation), size = 14.dp, contentDescription = mutation)
                        }
                    }
                }
            }
        }

        // Size bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${sizePercent.toInt()}", fontSize = 10.sp, color = AppTheme.colors.textSecondary, modifier = Modifier.width(28.dp))
            Box(modifier = Modifier.weight(1f)) {
                SizeBar(percent = sizePercent, color = color, showLabel = false)
            }
            if (crop.sellPrice != null) {
                Text(
                    PriceCalculator.formatFull(crop.sellPrice),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = onWater,
                enabled = canWater,
                modifier = Modifier.weight(1f).height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6),
                    disabledContainerColor = Color(0xFF3B82F6).copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = AppTheme.shapes.smallShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    if (canWater) "Water ($wateringCans)" else if (isMature) "Mature" else "No cans",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canWater) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
            Button(
                onClick = onHarvest,
                enabled = isMature,
                modifier = Modifier.weight(1f).height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.good,
                    disabledContainerColor = AppTheme.colors.good.copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = AppTheme.shapes.smallShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text(
                    if (isMature) "Harvest" else "Growing…",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMature) AppTheme.colors.onGood else AppTheme.colors.onGood.copy(alpha = 0.4f),
                )
            }
            // Cleanser picker for this slot: each held cleanser is enabled for its mutation groups
            CropCleanserPicker(
                cleansers = cropCleansers,
                cropMutations = crop.snapshot.mutations,
                onCleanse = onCleanse,
                modifier = Modifier.weight(1f),
                buttonHeight = 32.dp,
                fontSize = 12.sp,
                compact = true,
            )
            // Mutation potion picker for this slot; the server decides whether it applies
            MutationPotionPicker(
                potions = mutationPotions,
                onApplyPotion = onApplyPotion,
                modifier = Modifier.weight(1f),
                buttonHeight = 32.dp,
                fontSize = 12.sp,
                compact = true,
            )
        }
        }
    }
}
