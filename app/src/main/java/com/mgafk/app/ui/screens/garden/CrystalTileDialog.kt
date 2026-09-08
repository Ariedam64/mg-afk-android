package com.mgafk.app.ui.screens.garden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.model.CrystalType
import com.mgafk.app.data.model.PlacedCrystal
import com.mgafk.app.data.model.GardenTileRef
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.GardenSurfacePicker
import com.mgafk.app.ui.components.GardenTileState
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary

/**
 * Where to plant a shard: the whole garden, boardwalk included, since a crystal goes on either
 * surface. Tiles that already hold something are drawn but cannot be picked, because the game
 * refuses them and would say nothing about why.
 */
@Composable
fun CrystalTileDialog(
    type: CrystalType,
    occupiedTiles: Set<GardenTileRef>,
    crystals: List<PlacedCrystal>,
    apiReady: Boolean,
    onPick: (GardenTileRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(type, apiReady) { MgApi.findItem(type.toolId) }
    val crystalSprites = remember(crystals, apiReady) {
        crystals.associate { crystal ->
            GardenTileRef(crystal.tileType, crystal.localTileIndex) to
                MgApi.findItem(crystal.type.toolId)?.sprite
        }
    }
    val stateByTile = remember(occupiedTiles, crystalSprites) {
        occupiedTiles.associateWith { ref ->
            GardenTileState.Occupied(spriteUrl = crystalSprites[ref], full = ref !in crystalSprites)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(16.dp),
        ) {
            Text(
                "Plant ${entry?.name ?: type.toolId}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                "Pick an empty tile. The boardwalk works too.",
                fontSize = 10.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )

            GardenSurfacePicker(
                stateByTile = stateByTile,
                isSelectable = { it !in occupiedTiles },
                onTileClick = onPick,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
