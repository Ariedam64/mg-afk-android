package com.mgafk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mgafk.app.data.model.GardenTileType
import com.mgafk.app.data.repository.GardenGridLayout
import com.mgafk.app.data.model.GardenTileRef
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark

/** The planks the game draws its boardwalk with. */
private val BOARDWALK_COLOR = Color(0xFF8B6F47)

/** How one dirt tile looks in a [GardenTilePicker]. */
internal sealed interface GardenTileState {
    /** Nothing on the tile. */
    data object Free : GardenTileState

    /** Unavailable, and not because of what stands on it (reserved by another selection). */
    data object Locked : GardenTileState

    /**
     * Something stands on the tile.
     *
     * @param full whether it has no room left, which only dims it - whether it can still be
     *   tapped is the caller's call, via [GardenTilePicker]'s `isSelectable`.
     */
    data class Occupied(val spriteUrl: String?, val full: Boolean = false) : GardenTileState
}

/**
 * The garden drawn as a tappable dirt grid, shared by every flow that asks the player to point
 * at a tile: Auto-Plant's target selection, Auto-Dawn-Capture's, and manual planting.
 *
 * Only the look-up tables differ between those flows, so they stay with the caller: this draws
 * [stateByTile], highlights [selectedTiles], and reports taps on the tiles [isSelectable]
 * accepts. Pinch-zoom and drag-pan come from [ZoomableGardenGrid], since the full 2x10x10
 * layout doesn't fit a phone screen at a legible tile size.
 */
@Composable
internal fun GardenTilePicker(
    stateByTile: Map<Int, GardenTileState>,
    selectedTiles: Set<Int>,
    isSelectable: (tileId: Int) -> Boolean,
    onTileClick: (tileId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ZoomableGardenGrid(modifier = modifier) {
        for (row in 0 until GRID_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_SPACING)) {
                for (visualCol in 0 until GRID_COLS + 1) {
                    if (visualCol == BOARDWALK_VISUAL_COL) {
                        BoardwalkCell()
                    } else {
                        val col = if (visualCol < BOARDWALK_VISUAL_COL) visualCol else visualCol - 1
                        val tileId = row * GRID_COLS + col
                        GardenTileCell(
                            state = stateByTile[tileId] ?: GardenTileState.Free,
                            selected = tileId in selectedTiles,
                            selectable = isSelectable(tileId),
                            onClick = { onTileClick(tileId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The whole garden, boardwalk included, for the flows that can act on either surface.
 *
 * Crystals are the reason this exists: they plant on the dirt and on the boardwalk alike, so
 * the ring and the middle column stop being decoration and become targets. Cells are addressed
 * by [GardenTileRef] rather than by dirt index, since the two maps are numbered separately.
 */
@Composable
internal fun GardenSurfacePicker(
    stateByTile: Map<GardenTileRef, GardenTileState>,
    isSelectable: (GardenTileRef) -> Boolean,
    onTileClick: (GardenTileRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    ZoomableGardenGrid(modifier = modifier) {
        for (row in 0 until GardenGridLayout.ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_SPACING)) {
                for (col in 0 until GardenGridLayout.COLS) {
                    val ref = GardenGridLayout.tileAt(col, row)
                    if (ref == null) {
                        BoardwalkCell()
                        continue
                    }
                    GardenTileCell(
                        state = stateByTile[ref] ?: GardenTileState.Free,
                        selected = false,
                        selectable = isSelectable(ref),
                        onClick = { onTileClick(ref) },
                        boardwalk = ref.tileType == GardenTileType.Boardwalk,
                    )
                }
            }
        }
    }
}

@Composable
private fun GardenTileCell(
    state: GardenTileState,
    selected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
    boardwalk: Boolean = false,
) {
    val occupied = state is GardenTileState.Occupied
    val full = state is GardenTileState.Occupied && state.full
    val emptyColor = if (boardwalk) BOARDWALK_COLOR.copy(alpha = 0.5f) else SurfaceDark
    val backgroundColor = when {
        occupied -> StatusConnecting.copy(alpha = if (selected) 0.7f else if (full) 0.45f else 0.3f)
        selected -> StatusConnected.copy(alpha = 0.4f)
        state is GardenTileState.Locked -> emptyColor.copy(alpha = 0.3f)
        else -> emptyColor
    }
    val borderColor = when {
        selected -> StatusConnected
        occupied -> StatusConnecting.copy(alpha = 0.7f)
        state is GardenTileState.Locked -> SurfaceBorder.copy(alpha = 0.4f)
        else -> SurfaceBorder
    }

    Box(
        modifier = Modifier
            .size(CELL_SIZE)
            .clip(RoundedCornerShape(3.dp))
            .background(backgroundColor)
            .border(if (selected) 1.5.dp else 0.75.dp, borderColor, RoundedCornerShape(3.dp))
            .then(if (selectable) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val spriteUrl = (state as? GardenTileState.Occupied)?.spriteUrl
        if (spriteUrl != null) {
            SpriteImage(url = spriteUrl, size = CELL_SIZE - 4.dp, contentDescription = null)
        }
    }
}
