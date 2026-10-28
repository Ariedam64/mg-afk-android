package com.mgafk.app.ui.screens.pets

import com.mgafk.app.ui.theme.OnAccent
import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.model.PetTeam
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.abilityColor
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
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
import com.mgafk.app.data.repository.PetTileStrength

// ══════════════════════════════════════════
// Team Editor Dialog
// ══════════════════════════════════════════

@Composable
internal fun TeamEditorDialog(
    initialTeam: PetTeam?,
    isNew: Boolean,
    candidates: List<TeamPetCandidate>,
    apiReady: Boolean,
    strengthBonusByPetId: Map<String, Int>,
    onConfirm: (name: String, petIds: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var teamName by remember { mutableStateOf(initialTeam?.name.orEmpty()) }
    var slots by remember {
        mutableStateOf(
            List(PetTeam.MAX_PETS) { i ->
                val id = initialTeam?.petIds?.getOrElse(i) { "" }.orEmpty()
                if (id.isNotBlank()) candidates.find { it.id == id } else null
            }
        )
    }
    var pickerSlot by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(SurfaceCard)
                .padding(16.dp),
        ) {
            Text(
                if (isNew) "New Team" else "Edit Team",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = teamName,
                onValueChange = { teamName = it },
                label = { Text("Team name") },
                placeholder = { Text("My team", color = TextMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedLabelColor = Accent,
                    cursorColor = Accent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Pets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (i in 0 until PetTeam.MAX_PETS) {
                    Box(modifier = Modifier.weight(1f)) {
                        val pet = slots[i]
                        if (pet != null) {
                            FilledSlotTile(
                                pet = pet,
                                apiReady = apiReady,
                                strengthBonus = strengthBonusByPetId[pet.id] ?: 0,
                                onRemove = { slots = slots.toMutableList().also { it[i] = null } },
                            )
                        } else {
                            EmptySlotTile(onClick = { pickerSlot = i })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                ) {
                    Text("Cancel", fontSize = 12.sp, color = TextSecondary)
                }
                Button(
                    onClick = {
                        val chosen = slots.filterNotNull()
                        val name = teamName.trim().ifBlank {
                            chosen.joinToString(" / ") { it.name.ifBlank { it.species } }.ifBlank { "Team" }
                        }
                        // The server truncates past this, so do it here and show the real result.
                        onConfirm(name.take(PetTeam.MAX_NAME_CLUSTERS), chosen.map { it.id })
                    },
                    enabled = slots.any { it != null },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Text(if (isNew) "Create" else "Save", fontSize = 12.sp, color = OnAccent)
                }
            }
        }
    }

    pickerSlot?.let { slotIdx ->
        val usedIds = slots.filterNotNull().map { it.id }.toSet()
        val available = remember(candidates, usedIds) {
            candidates.filter { it.id !in usedIds }.sortedBy { it.species.lowercase() }
        }

        PetPickerDialog(
            candidates = available,
            apiReady = apiReady,
            onSelect = { selected ->
                slots = slots.toMutableList().also { it[slotIdx] = selected }
                pickerSlot = null
            },
            onDismiss = { pickerSlot = null },
        )
    }
}

// ── Empty slot "+" ──

@Composable
private fun EmptySlotTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(AppTheme.shapes.smallShape)
            .border(1.5.dp, Accent.copy(alpha = 0.3f), AppTheme.shapes.smallShape)
            .background(SurfaceDark)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
        Text("Add pet", fontSize = 8.sp, color = TextMuted, lineHeight = 10.sp)
    }
}

// ── Filled slot (pet tile + remove X) ──

@Composable
private fun FilledSlotTile(
    pet: TeamPetCandidate,
    apiReady: Boolean,
    strengthBonus: Int,
    onRemove: () -> Unit,
) {
    val entry = remember(pet.species, apiReady) { MgApi.findPet(pet.species) }
    val displayName = pet.name.ifBlank { entry?.name ?: pet.species }
    val rarityId = entry?.rarity
    val ms = PetTileStrength.maxStr(pet.species, pet.targetScale)
    val cs = PetTileStrength.curStr(pet.species, pet.xp, ms)
    val isMax = cs >= ms

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(AppTheme.shapes.smallShape)
            .rarityBorder(rarity = rarityId, width = 1.5.dp, shape = AppTheme.shapes.smallShape, alpha = 0.5f)
            .background(SurfaceDark),
    ) {
        // Remove button top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(StatusError.copy(alpha = 0.15f))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, "Remove", tint = StatusError.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
        }

        // Mutations top-left
        if (pet.mutations.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                sortMutations(pet.mutations).take(2).forEach { SpriteImage(url = mutationSpriteUrl(it), size = 12.dp, contentDescription = it) }
            }
        }

        // Center content
        Column(
            modifier = Modifier.align(Alignment.Center).padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpriteImage(category = "pets", name = pet.species, size = 28.dp, contentDescription = pet.species, mutations = pet.mutations)
            Text(
                displayName, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp,
            )
            if (ms > 0) {
                val strText = when {
                    strengthBonus > 0 -> "STR ${cs + strengthBonus} (+$strengthBonus)"
                    isMax -> "STR $cs"
                    else -> "STR $cs/$ms"
                }
                val strColor = if (isMax || strengthBonus > 0) StatusConnecting else Accent
                Text(strText, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = strColor, lineHeight = 9.sp)
            }
            if (pet.abilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    pet.abilities.forEach { id ->
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
