package com.mgafk.app.ui.screens.pets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.model.PetTeam
import com.mgafk.app.data.repository.MgApi
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
import kotlin.math.roundToInt
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations

// ── Shared constants ──

private val TILE_MIN = 76.dp
private val GAP = 6.dp

private val RarityCommon = Color(0xFFE7E7E7)
private val RarityUncommon = Color(0xFF67BD4D)
private val RarityRare = Color(0xFF0071C6)
private val RarityLegendary = Color(0xFFFFC734)
private val RarityMythical = Color(0xFF9944A7)
private val RarityDivine = Color(0xFFFF7835)
private val RarityCelestial = Color(0xFFFF00FF)

private fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "common" -> RarityCommon; "uncommon" -> RarityUncommon; "rare" -> RarityRare
    "legendary" -> RarityLegendary; "mythical", "mythic" -> RarityMythical
    "divine" -> RarityDivine; "celestial" -> RarityCelestial; else -> TextMuted
}

private val RARITY_ORDER = listOf("Celestial", "Divine", "Mythic", "Mythical", "Legendary", "Rare", "Uncommon", "Common")
private fun raritySortPet(species: String): Int {
    val rarity = MgApi.findPet(species)?.rarity ?: return RARITY_ORDER.size
    return RARITY_ORDER.indexOfFirst { it.equals(rarity, ignoreCase = true) }.let { if (it < 0) RARITY_ORDER.size else it }
}

private const val XP_H = 3600.0; private const val BASE_S = 80; private const val MAX_S = 100; private const val S_GAIN = 30
private fun maxStr(sp: String, sc: Double): Int {
    val ms = MgApi.findPet(sp)?.maxScale ?: return BASE_S
    if (sc <= 1.0) return BASE_S; if (sc >= ms) return MAX_S
    return (BASE_S + 20 * (sc - 1.0) / (ms - 1.0)).toInt()
}
private fun curStr(sp: String, xp: Double, max: Int): Int {
    val htm = MgApi.findPet(sp)?.hoursToMature ?: return max - S_GAIN
    return ((max - S_GAIN) + minOf(S_GAIN / htm * (xp / XP_H), S_GAIN.toDouble())).toInt()
}

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

/** Unified pet candidate for the team editor picker. */
data class TeamPetCandidate(
    val id: String,
    val species: String,
    val name: String,
    val xp: Double,
    val targetScale: Double,
    val mutations: List<String>,
    val abilities: List<String>,
)

// ══════════════════════════════════════════
// Main Card
// ══════════════════════════════════════════

@Composable
fun PetTeamCard(
    teams: List<PetTeam>,
    activePets: List<PetSnapshot>,
    inventoryPets: List<InventoryPetItem>,
    hutchPets: List<InventoryPetItem>,
    activeTeamId: String?,
    apiReady: Boolean,
    onCreate: (PetTeam) -> Unit,
    onUpdate: (PetTeam) -> Unit,
    onDelete: (teamId: String) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onActivate: (PetTeam) -> Unit,
    showTip: Boolean = false,
    onDismissTip: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val allCandidates = remember(activePets, inventoryPets, hutchPets) {
        buildCandidateList(activePets, inventoryPets, hutchPets)
    }
    // Lookup for current mutations by pet id — used by team rows to render composed
    // sprites with each pet's actual mutations (the team itself only stores species).
    val mutationsByPetId = remember(allCandidates) {
        allCandidates.associate { it.id to it.mutations }
    }

    var editorTeam by remember { mutableStateOf<PetTeam?>(null) }
    var editorIsNew by remember { mutableStateOf(false) }

    // Drag state
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }

    AppCard(
        modifier = modifier,
        title = "Pet Teams",
        collapsible = true,
        persistKey = "pets.teams",
        trailing = {
            Text("${teams.size}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
        },
    ) {
        // First-time tip
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
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Create pet teams and tap one to swap your active pets instantly.",
                        fontSize = 11.sp,
                        color = Accent,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "OK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        modifier = Modifier.clickable { onDismissTip() },
                    )
                }
            }
        }

        if (teams.isEmpty()) {
            AddTeamTile {
                editorTeam = PetTeam()
                editorIsNew = true
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                teams.forEachIndexed { index, team ->
                    val isDragging = dragIndex == index
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 0.dp,
                        label = "dragElevation",
                    )

                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .offset {
                                IntOffset(0, if (isDragging) dragOffsetY.roundToInt() else 0)
                            }
                            .then(if (isDragging) Modifier.shadow(elevation, RoundedCornerShape(10.dp)) else Modifier)
                            .onGloballyPositioned { coords ->
                                if (rowHeight == 0) rowHeight = coords.size.height
                            },
                    ) {
                        TeamRow(
                            team = team,
                            mutationsByPetId = mutationsByPetId,
                            isActive = team.id == activeTeamId,
                            onActivate = { onActivate(team) },
                            onEdit = {
                                editorTeam = team
                                editorIsNew = false
                            },
                            onDelete = { onDelete(team.id) },
                            dragModifier = Modifier.pointerInput(teams.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragIndex = index
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        // Check if we crossed into another row
                                        if (rowHeight > 0) {
                                            val rowsOffset = (dragOffsetY / (rowHeight + 8.dp.toPx())).roundToInt()
                                            val targetIndex = (dragIndex + rowsOffset).coerceIn(0, teams.size - 1)
                                            if (targetIndex != dragIndex) {
                                                onReorder(dragIndex, targetIndex)
                                                dragOffsetY -= (targetIndex - dragIndex) * (rowHeight + 8.dp.toPx())
                                                dragIndex = targetIndex
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        dragIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        dragIndex = -1
                                        dragOffsetY = 0f
                                    },
                                )
                            },
                        )
                    }
                }
                if (teams.size < PetTeam.MAX_TEAMS) {
                    AddTeamTile {
                        editorTeam = PetTeam()
                        editorIsNew = true
                    }
                }
            }
        }
    }

    editorTeam?.let { team ->
        TeamEditorDialog(
            initialTeam = team,
            isNew = editorIsNew,
            candidates = allCandidates,
            apiReady = apiReady,
            onConfirm = { finalTeam ->
                if (editorIsNew) onCreate(finalTeam) else onUpdate(finalTeam)
                editorTeam = null
            },
            onDismiss = { editorTeam = null },
        )
    }
}

// ══════════════════════════════════════════
// Add tile
// ══════════════════════════════════════════

@Composable
private fun AddTeamTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
        Text("New team", fontSize = 9.sp, color = TextMuted, lineHeight = 11.sp)
    }
}

// ══════════════════════════════════════════
// Team row — single line: drag | sprites | name | badge | edit | delete
// ══════════════════════════════════════════

@Composable
private fun TeamRow(
    team: PetTeam,
    mutationsByPetId: Map<String, List<String>>,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val borderColor = if (isActive) StatusConnected.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.10f)
    // Pair each filled species with the pet id at the same slot index so we can look
    // up the pet's current mutations for composed sprite rendering.
    val filledSlots = remember(team.petSpecies, team.petIds) {
        team.petSpecies.zip(team.petIds).filter { (species, _) -> species.isNotBlank() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(if (isActive) StatusConnected.copy(alpha = 0.06f) else SurfaceBorder.copy(alpha = 0.08f))
            .clickable { onActivate() }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Drag handle (left)
        Icon(
            Icons.Outlined.DragIndicator,
            contentDescription = "Reorder",
            tint = TextMuted,
            modifier = dragModifier.size(20.dp),
        )

        // Pet sprites (only filled slots, with spacing) — composed with each pet's
        // current mutations when available.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            filledSlots.forEach { (species, petId) ->
                val mutations = mutationsByPetId[petId] ?: emptyList()
                SpriteImage(
                    category = "pets",
                    name = species,
                    size = 26.dp,
                    contentDescription = species,
                    mutations = mutations,
                )
            }
        }

        // Name
        Text(
            team.name.ifBlank { "Unnamed" },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Edit button (right)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Accent.copy(alpha = 0.10f))
                .clickable { onEdit() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Edit, "Edit", tint = Accent, modifier = Modifier.size(15.dp))
        }

        // Delete button (right)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(StatusError.copy(alpha = 0.08f))
                .clickable { showDeleteConfirm = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DeleteOutline, "Delete", tint = StatusError.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = SurfaceDark,
            title = { Text("Delete team?", color = TextPrimary) },
            text = { Text("Remove \"${team.name.ifBlank { "Unnamed" }}\" from your teams?", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = StatusError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }
}

// ══════════════════════════════════════════
// Team Editor Dialog
// ══════════════════════════════════════════

@Composable
private fun TeamEditorDialog(
    initialTeam: PetTeam,
    isNew: Boolean,
    candidates: List<TeamPetCandidate>,
    apiReady: Boolean,
    onConfirm: (PetTeam) -> Unit,
    onDismiss: () -> Unit,
) {
    var teamName by remember { mutableStateOf(initialTeam.name) }
    var slots by remember {
        mutableStateOf(
            List(PetTeam.MAX_PETS) { i ->
                val id = initialTeam.petIds.getOrElse(i) { "" }
                if (id.isNotBlank()) candidates.find { it.id == id } else null
            }
        )
    }
    var pickerSlot by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
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
                        val team = initialTeam.copy(
                            name = teamName.trim().ifBlank {
                                slots.filterNotNull().joinToString(" / ") { it.name.ifBlank { it.species } }.ifBlank { "Team" }
                            },
                            petIds = List(PetTeam.MAX_PETS) { i -> slots[i]?.id ?: "" },
                            petSpecies = List(PetTeam.MAX_PETS) { i -> slots[i]?.species ?: "" },
                            petNames = List(PetTeam.MAX_PETS) { i -> slots[i]?.name ?: "" },
                        )
                        onConfirm(team)
                    },
                    enabled = slots.any { it != null },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Text(if (isNew) "Create" else "Save", fontSize = 12.sp, color = Color.White)
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
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
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
    onRemove: () -> Unit,
) {
    val entry = remember(pet.species, apiReady) { MgApi.findPet(pet.species) }
    val displayName = pet.name.ifBlank { entry?.name ?: pet.species }
    val rarityId = entry?.rarity
    val ms = maxStr(pet.species, pet.targetScale)
    val cs = curStr(pet.species, pet.xp, ms)
    val isMax = cs >= ms

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(10.dp))
            .rarityBorder(rarity = rarityId, width = 1.5.dp, shape = RoundedCornerShape(10.dp), alpha = 0.5f)
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
                val strText = if (isMax) "STR $cs" else "STR $cs/$ms"
                val strColor = if (isMax) Color(0xFFFBBF24) else Accent
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

// ══════════════════════════════════════════
// Pet Picker Dialog
// ══════════════════════════════════════════

@Composable
private fun PetPickerDialog(
    candidates: List<TeamPetCandidate>,
    apiReady: Boolean,
    onSelect: (TeamPetCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
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
                    columns = GridCells.Adaptive(TILE_MIN),
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                    verticalArrangement = Arrangement.spacedBy(GAP),
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

/** Pet tile in picker — same visual style as PetHutchCard. */
@Composable
private fun PickerPetTile(
    candidate: TeamPetCandidate,
    apiReady: Boolean,
    onClick: () -> Unit,
) {
    val entry = remember(candidate.species, apiReady) { MgApi.findPet(candidate.species) }
    val displayName = candidate.name.ifBlank { entry?.name ?: candidate.species }
    val rarityId = entry?.rarity
    val ms = maxStr(candidate.species, candidate.targetScale)
    val cs = curStr(candidate.species, candidate.xp, ms)
    val isMax = cs >= ms

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .rarityBorder(rarity = rarityId, width = 1.5.dp, shape = RoundedCornerShape(10.dp), alpha = 0.5f)
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
            val strColor = if (isMax) Color(0xFFFBBF24) else Accent
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

// ══════════════════════════════════════════
// Helpers
// ══════════════════════════════════════════

private fun buildCandidateList(
    activePets: List<PetSnapshot>,
    inventoryPets: List<InventoryPetItem>,
    hutchPets: List<InventoryPetItem>,
): List<TeamPetCandidate> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<TeamPetCandidate>()

    for (pet in activePets) {
        if (pet.id.isBlank() || !seen.add(pet.id)) continue
        result += TeamPetCandidate(
            id = pet.id, species = pet.species, name = pet.name,
            xp = pet.xp, targetScale = pet.targetScale,
            mutations = pet.mutations, abilities = pet.abilities,
        )
    }
    for (pet in inventoryPets) {
        if (pet.id.isBlank() || !seen.add(pet.id)) continue
        result += TeamPetCandidate(
            id = pet.id, species = pet.petSpecies, name = pet.name ?: "",
            xp = pet.xp, targetScale = pet.targetScale,
            mutations = pet.mutations, abilities = pet.abilities,
        )
    }
    for (pet in hutchPets) {
        if (pet.id.isBlank() || !seen.add(pet.id)) continue
        result += TeamPetCandidate(
            id = pet.id, species = pet.petSpecies, name = pet.name ?: "",
            xp = pet.xp, targetScale = pet.targetScale,
            mutations = pet.mutations, abilities = pet.abilities,
        )
    }

    return result
}
