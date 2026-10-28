package com.mgafk.app.ui.screens.pets

import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.abilityColor
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.theme.rarityBorder
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations

// ── Pet picker dialog (used for both Swap and Equip) ──

@Composable
internal fun PetPickerDialog(
    title: String,
    candidates: List<SwapCandidate>,
    apiReady: Boolean,
    onSelect: (SwapCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
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
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = rarity, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
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
            val strColor = if (isMaxStr) StatusConnecting else Accent
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
