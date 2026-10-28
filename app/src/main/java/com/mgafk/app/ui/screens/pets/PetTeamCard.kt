package com.mgafk.app.ui.screens.pets

import com.mgafk.app.ui.theme.AppTheme
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.model.PetTeam
import com.mgafk.app.data.model.PetTeamEmblem
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

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
    /** Flat strength a Strength crystal is granting, which reaches equipped pets only. */
    strengthBonus: Int = 0,
    onCreate: (name: String, petIds: List<String>) -> Unit,
    onUpdate: (teamId: String, name: String, petIds: List<String>) -> Unit,
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
    // Lookup for current mutations by pet id - used by team rows to render composed
    // sprites with each pet's actual mutations (the team itself only stores species).
    val mutationsByPetId = remember(allCandidates) {
        allCandidates.associate { it.id to it.mutations }
    }
    // A Strength crystal only reaches the pets that are actually equipped, so a team sitting in
    // the hutch shows its pets' own strength.
    val strengthBonusByPetId = remember(activePets, strengthBonus) {
        if (strengthBonus <= 0) emptyMap() else activePets.associate { it.id to strengthBonus }
    }

    var editorTeam by remember { mutableStateOf<PetTeam?>(null) }
    var editorIsNew by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }

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
                    .clip(AppTheme.shapes.smallShape)
                    .background(Accent.copy(alpha = 0.1f))
                    .border(1.dp, Accent.copy(alpha = 0.25f), AppTheme.shapes.smallShape)
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
                editorTeam = null
                editorIsNew = true
                editorOpen = true
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
                            .then(if (isDragging) Modifier.shadow(elevation, AppTheme.shapes.smallShape) else Modifier)
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
                                editorOpen = true
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
                        editorTeam = null
                        editorIsNew = true
                        editorOpen = true
                    }
                } else {
                    // The server refuses a 26th team, so say why instead of offering the tile.
                    Text(
                        "Team limit reached (${PetTeam.MAX_TEAMS} of ${PetTeam.MAX_TEAMS})",
                        fontSize = 10.sp,
                        color = TextMuted,
                    )
                }
            }
        }
    }

    if (editorOpen) {
        TeamEditorDialog(
            initialTeam = editorTeam,
            isNew = editorIsNew,
            candidates = allCandidates,
            apiReady = apiReady,
            strengthBonusByPetId = strengthBonusByPetId,
            onConfirm = { name, petIds ->
                val existing = editorTeam
                if (editorIsNew || existing == null) onCreate(name, petIds)
                else onUpdate(existing.id, name, petIds)
                editorOpen = false
                editorTeam = null
            },
            onDismiss = { editorOpen = false; editorTeam = null },
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
            .clip(AppTheme.shapes.smallShape)
            .border(1.5.dp, Accent.copy(alpha = 0.3f), AppTheme.shapes.smallShape)
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
// Team row - single line: drag | sprites | name | badge | edit | delete
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
    val filledSlots = remember(team.members) {
        team.members.filter { it.petSpecies.isNotBlank() }.map { it.petSpecies to it.petId }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.smallShape)
            .border(1.dp, borderColor, AppTheme.shapes.smallShape)
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

        TeamEmblemBadge(team.emblem)

        // Pet sprites (only filled slots, with spacing) - composed with each pet's
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

/**
 * The team's badge, as the game assigns it. Read-only here: the server picks one on create and
 * the game is where a player changes it.
 *
 * A letter renders as text, the sprite-backed kinds through [SpriteImage], and an emblem this
 * build does not know renders as nothing rather than as a wrong guess.
 */
@Composable
private fun TeamEmblemBadge(emblem: PetTeamEmblem) {
    val size = 22.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(5.dp))
            .background(SurfaceBorder.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        when (emblem) {
            is PetTeamEmblem.Letter -> Text(
                emblem.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
            )
            is PetTeamEmblem.Pet -> SpriteImage(
                category = "pets",
                name = emblem.petSpecies,
                size = size - 4.dp,
                contentDescription = emblem.petSpecies,
            )
            is PetTeamEmblem.Cosmetic -> SpriteImage(
                url = MgApi.findItem(emblem.cosmetic)?.sprite,
                size = size - 4.dp,
                contentDescription = emblem.cosmetic,
            )
            // The game draws these from its own icon atlas, which the app has no mapping for.
            is PetTeamEmblem.Icon -> Text(
                emblem.icon.take(1).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
            )
            PetTeamEmblem.Unknown -> Unit
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
