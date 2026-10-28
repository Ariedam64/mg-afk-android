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
import com.mgafk.app.data.repository.PetTileStrength
import com.mgafk.app.ui.components.TILE_MIN_WIDTH
import com.mgafk.app.ui.components.TILE_SPACING

// ══════════════════════════════════════════
// Pet Picker Dialog
// ══════════════════════════════════════════

@Composable
internal fun PetPickerDialog(
    candidates: List<TeamPetCandidate>,
    apiReady: Boolean,
    onSelect: (TeamPetCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(SurfaceCard)
                .padding(16.dp),
        ) {
            Text("Select a pet", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${candidates.size} pets available", fontSize = 11.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(12.dp))

            if (candidates.isEmpty()) {
                Text("No pets available.", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(TILE_MIN_WIDTH),
                    horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                    verticalArrangement = Arrangement.spacedBy(TILE_SPACING),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 100.dp)
                        .height(360.dp),
                ) {
                    items(candidates, key = { it.id }) { candidate ->
                        PickerPetTile(candidate = candidate, apiReady = apiReady, onClick = { onSelect(candidate) })
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

/** Pet tile in picker - same visual style as PetHutchCard. */
@Composable
private fun PickerPetTile(
    candidate: TeamPetCandidate,
    apiReady: Boolean,
    onClick: () -> Unit,
) {
    val entry = remember(candidate.species, apiReady) { MgApi.findPet(candidate.species) }
    val displayName = candidate.name.ifBlank { entry?.name ?: candidate.species }
    val rarityId = entry?.rarity
    val ms = PetTileStrength.maxStr(candidate.species, candidate.targetScale)
    val cs = PetTileStrength.curStr(candidate.species, candidate.xp, ms)
    val isMax = cs >= ms

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = rarityId, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
            .background(SurfaceDark)
            .clickable(onClick = onClick),
    ) {
        if (candidate.mutations.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                sortMutations(candidate.mutations).take(2).forEach { SpriteImage(url = mutationSpriteUrl(it), size = 12.dp, contentDescription = it) }
            }
        }
        if (ms > 0) {
            val strText = if (isMax) "$cs" else "$cs/$ms"
            val strColor = if (isMax) StatusConnecting else Accent
            Text(
                strText, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                color = strColor, lineHeight = 9.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpriteImage(category = "pets", name = candidate.species, size = 28.dp, contentDescription = candidate.species, mutations = candidate.mutations)
            Text(
                displayName, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp,
            )
            if (candidate.abilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    candidate.abilities.forEach { id ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(abilityColor(id)),
                        )
                    }
                }
            }
        }
    }
}
