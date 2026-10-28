package com.mgafk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PetTileStrength
import com.mgafk.app.ui.theme.AppTheme
import com.mgafk.app.ui.theme.rarityBorder

/** Compact pet card for the inventory and Pet Hutch grids, same style as the pet selector. */
@Composable
internal fun PetTile(pet: InventoryPetItem, apiReady: Boolean) {
    val entry = remember(pet.petSpecies, apiReady) { MgApi.findPet(pet.petSpecies) }
    val name = pet.name?.ifBlank { null } ?: entry?.name ?: pet.petSpecies
    val rarityId = entry?.rarity
    val ms = PetTileStrength.maxStr(pet.petSpecies, pet.targetScale)
    val cs = PetTileStrength.curStr(pet.petSpecies, pet.xp, ms)
    val isMax = cs >= ms

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = rarityId, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
            .background(AppTheme.colors.surfaceHigh),
    ) {
        // Mutation icons top-left
        if (pet.mutations.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                sortMutations(pet.mutations).take(2).forEach {
                    SpriteImage(url = mutationSpriteUrl(it), size = 12.dp, contentDescription = it)
                }
            }
        }
        // Center content - STR goes below the name (top-right is reserved for the
        // lock icon in the Pet Hutch card)
        Column(
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpriteImage(category = "pets", name = pet.petSpecies, size = 28.dp, contentDescription = pet.petSpecies, mutations = pet.mutations)
            Text(
                name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = AppTheme.colors.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp,
            )
            if (ms > 0) {
                val strText = if (isMax) "$cs" else "$cs/$ms"
                val strColor = if (isMax) AppTheme.colors.warn else AppTheme.colors.accent
                Text(strText, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = strColor, lineHeight = 9.sp)
            }
            if (pet.abilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    pet.abilities.forEach { abilityId ->
                        val bg = remember(abilityId, apiReady) { abilityBrush(abilityId) }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(bg),
                        )
                    }
                }
            }
        }
    }
}
