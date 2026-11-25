package com.mgafk.app.ui.screens.garden

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.repository.CropCleansers
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.MutationPotions
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.rarityColor
import androidx.compose.ui.window.Dialog
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import com.mgafk.app.ui.components.formatTimeRemaining
import com.mgafk.app.ui.components.formatQuantity

/** A held potion is always usable; a Tool Shack one only when the inventory can take it back. */
internal fun isMutationPotionUsable(potion: MutationPotions.Offer): Boolean =
    potion.source == MutationPotions.Source.INVENTORY || potion.canRetrieve

/** "Name xN (+M in Tool Shack)" for a held potion, "Name xM · Tool Shack" for a stored one. */
internal fun mutationPotionLabel(potion: MutationPotions.Offer, name: String): String = when {
    potion.source == MutationPotions.Source.INVENTORY && potion.storageQuantity > 0 ->
        "$name x${formatQuantity(potion.inventoryQuantity)} (+${formatQuantity(potion.storageQuantity)} in Tool Shack)"
    potion.source == MutationPotions.Source.INVENTORY ->
        "$name x${formatQuantity(potion.inventoryQuantity)}"
    !potion.canRetrieve ->
        "$name x${formatQuantity(potion.storageQuantity)} · Tool Shack — Inventory full — free a slot"
    else -> "$name x${formatQuantity(potion.storageQuantity)} · Tool Shack"
}

// ── Single crop detail dialog ──

@Composable
internal fun PlantDetailDialog(
    plant: ResolvedPlant,
    wateringCans: Int,
    planterPots: Int,
    cropCleansers: List<CropCleansers.Offer>,
    onHarvest: () -> Unit,
    onWater: () -> Unit,
    onPot: () -> Unit,
    onCleanse: (toolId: String) -> Unit,
    onDismiss: () -> Unit,
    mutationPotions: List<MutationPotions.Offer> = emptyList(),
    onApplyPotion: (MutationPotions.Offer) -> Unit = {},
) {
    val color = rarityColor(plant.rarity)
    val sizePercent = plant.snapshot.size.toDouble()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(AppTheme.colors.surface)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Sprite
            SpriteImage(
                category = "plants",
                name = plant.snapshot.species,
                size = 56.dp,
                contentDescription = plant.displayName,
                mutations = plant.snapshot.mutations,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Name
            Text(
                plant.displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
            )

            // Rarity
            if (plant.rarity != null) {
                Text(
                    plant.rarity,
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
                // Size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Size", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                    Text("${sizePercent.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary)
                }
                SizeBar(percent = sizePercent, color = color, showLabel = false)

                // Sell price
                if (plant.sellPrice != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Sell price", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                        Text(
                            PriceCalculator.formatFull(plant.sellPrice),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                        )
                    }
                }

                // Tile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Tile", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                    Text("#${plant.snapshot.tileId}", fontSize = 12.sp, color = AppTheme.colors.textPrimary)
                }

                if (plant.snapshot.preserved) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Preserved", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("weather cannot change it", fontSize = 11.sp, color = AppTheme.colors.textMuted)
                            SpriteImage(url = MgApi.preservationSpriteUrl, size = 16.dp, contentDescription = "Preserved")
                        }
                    }
                }

                // Mutations
                if (plant.snapshot.mutations.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Mutations", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            sortMutations(plant.snapshot.mutations).forEach { mutation ->
                                SpriteImage(url = mutationSpriteUrl(mutation), size = 16.dp, contentDescription = mutation)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tick every second for live countdown
            val now = rememberSecondTick()
            val isMature = plant.snapshot.endTime > 0 && now >= plant.snapshot.endTime
            val canWater = !isMature && wateringCans > 0

            // Remaining time info (shown above buttons when growing)
            if (!isMature) {
                val remaining = formatTimeRemaining(plant.snapshot.endTime, System.currentTimeMillis())
                if (remaining.isNotEmpty()) {
                    Text(
                        remaining,
                        fontSize = 11.sp,
                        color = AppTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    )
                }
            }

            val canPot = planterPots > 0
            val potSprite = remember { MgApi.findItem("PlanterPot")?.sprite }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Water button
                Button(
                    onClick = onWater,
                    enabled = canWater,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        disabledContainerColor = Color(0xFF3B82F6).copy(alpha = 0.2f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f),
                    ),
                    shape = AppTheme.shapes.smallShape,
                ) {
                    Text(
                        if (!isMature && wateringCans > 0) "Water ($wateringCans)"
                        else if (isMature) "Mature"
                        else "No cans",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canWater) Color.White else Color.White.copy(alpha = 0.4f),
                    )
                }

                // Harvest button
                Button(
                    onClick = onHarvest,
                    enabled = isMature,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppTheme.colors.good,
                        disabledContainerColor = AppTheme.colors.good.copy(alpha = 0.2f),
                        disabledContentColor = Color.White.copy(alpha = 0.4f),
                    ),
                    shape = AppTheme.shapes.smallShape,
                ) {
                    Text(
                        if (isMature) "Harvest" else "Growing…",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMature) AppTheme.colors.onGood else AppTheme.colors.onGood.copy(alpha = 0.4f),
                    )
                }
            }

            // Cleanse button (separate row): one entry per held cleanser, enabled for its mutation groups
            CropCleanserPicker(
                cleansers = cropCleansers,
                cropMutations = plant.snapshot.mutations,
                onCleanse = onCleanse,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.sp,
            )

            // One Potion button opening the held and Tool Shack potions; the server decides whether it applies
            MutationPotionPicker(
                potions = mutationPotions,
                onApplyPotion = onApplyPotion,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.sp,
            )

            // Pot button (separate row)
            Button(
                onClick = onPot,
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
