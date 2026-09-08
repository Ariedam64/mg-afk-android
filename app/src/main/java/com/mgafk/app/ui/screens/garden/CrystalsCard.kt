package com.mgafk.app.ui.screens.garden

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.CrystalType
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.model.PlacedCrystal
import com.mgafk.app.data.repository.Crystals
import com.mgafk.app.data.model.GardenTileRef
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * The crystals planted in the garden and the shards left to plant.
 *
 * A crystal's time only runs while the player is in the room, so the countdowns tick from
 * [crystalsReadAtMs] and freeze the moment the session drops: an app that kept counting would
 * show a crystal as burnt out while it is still running in game.
 */
@Composable
fun CrystalsCard(
    crystals: List<PlacedCrystal>,
    tools: List<InventoryToolItem>,
    connected: Boolean,
    crystalsReadAtMs: Long,
    occupiedTiles: Set<GardenTileRef>,
    apiReady: Boolean,
    onPlant: (CrystalType, GardenTileRef) -> Unit,
    onFuse: (PlacedCrystal) -> Unit,
    onPickup: (PlacedCrystal) -> Unit,
    modifier: Modifier = Modifier,
) {
    var plantingType by remember { mutableStateOf<CrystalType?>(null) }

    AppCard(
        modifier = modifier,
        title = "Crystals",
        collapsible = true,
        persistKey = "garden.crystals",
    ) {
        // One clock for every countdown, and only while the session is live.
        var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(connected) {
            while (connected) {
                nowMs = System.currentTimeMillis()
                delay(1000)
            }
        }
        val elapsedSeconds = if (connected && crystalsReadAtMs > 0) {
            ((nowMs - crystalsReadAtMs) / 1000).coerceAtLeast(0L).toInt()
        } else 0

        val standing = crystals.map { it to (it.remainingSeconds - elapsedSeconds).coerceAtLeast(0) }
        val effects = remember(crystals, elapsedSeconds) {
            Crystals.effects(standing.map { (crystal, seconds) -> crystal.copy(remainingSeconds = seconds) })
        }

        if (standing.isEmpty()) {
            Text("No crystal planted.", fontSize = 11.sp, color = TextMuted)
        } else {
            for ((crystal, seconds) in standing.sortedByDescending { it.second }) {
                PlantedCrystalRow(
                    crystal = crystal,
                    remainingSeconds = seconds,
                    apiReady = apiReady,
                    canFuse = Crystals.chooseForFuse(
                        Crystals.ownedShards(crystal.type, tools),
                        targetSeconds = seconds,
                    ) != null,
                    onFuse = { onFuse(crystal.copy(remainingSeconds = seconds)) },
                    onPickup = { onPickup(crystal) },
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        if (effects.hasAny) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                effectsSummary(effects.hungerRateMultiplier, effects.xpRateMultiplier, effects.strengthBonus),
                fontSize = 10.sp,
                color = StatusConnected,
                lineHeight = 13.sp,
            )
        }

        val owned = CrystalType.entries.mapNotNull { type ->
            val count = tools.filter { it.toolId == type.toolId }.sumOf { it.quantity }
            if (count > 0) type to count else null
        }
        if (owned.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Shards", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(modifier = Modifier.height(6.dp))
            for ((type, count) in owned) {
                ShardRow(
                    type = type,
                    count = count,
                    apiReady = apiReady,
                    atLimit = !Crystals.canPlace(
                        type,
                        standing.map { (crystal, seconds) -> crystal.copy(remainingSeconds = seconds) },
                    ),
                    onPlant = { plantingType = type },
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    plantingType?.let { type ->
        CrystalTileDialog(
            type = type,
            occupiedTiles = occupiedTiles,
            crystals = crystals,
            apiReady = apiReady,
            onPick = { ref ->
                plantingType = null
                onPlant(type, ref)
            },
            onDismiss = { plantingType = null },
        )
    }
}

@Composable
private fun PlantedCrystalRow(
    crystal: PlacedCrystal,
    remainingSeconds: Int,
    apiReady: Boolean,
    canFuse: Boolean,
    onFuse: () -> Unit,
    onPickup: () -> Unit,
) {
    val entry = remember(crystal.type, apiReady) { MgApi.findItem(crystal.type.toolId) }
    val expired = remainingSeconds <= 0
    val atCeiling = Crystals.isAtMaxLifespan(remainingSeconds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SpriteImage(url = entry?.sprite, size = 26.dp, contentDescription = crystal.type.id)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                crystalName(entry?.name, crystal.type),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (expired) "burnt out, ${crystal.tileType.name.lowercase()}"
                else "${formatDuration(remainingSeconds)} left, ${crystal.tileType.name.lowercase()}",
                fontSize = 10.sp,
                color = if (expired) TextMuted else TextSecondary,
                lineHeight = 13.sp,
            )
        }
        if (canFuse && !atCeiling) {
            RowAction(label = "Fuse", color = Accent, onClick = onFuse)
        }
        RowAction(label = "Take", color = TextSecondary, onClick = onPickup)
    }
}

@Composable
private fun ShardRow(
    type: CrystalType,
    count: Int,
    apiReady: Boolean,
    atLimit: Boolean,
    onPlant: () -> Unit,
) {
    val entry = remember(type, apiReady) { MgApi.findItem(type.toolId) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorder.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SpriteImage(url = entry?.sprite, size = 26.dp, contentDescription = type.toolId)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry?.name ?: type.toolId,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (atLimit) "x$count, garden already holds ${type.activeLimit}"
                else "x$count, ${shardEffect(type)}",
                fontSize = 10.sp,
                color = TextMuted,
                lineHeight = 13.sp,
            )
        }
        if (!atLimit) {
            RowAction(label = "Plant", color = Accent, onClick = onPlant)
        }
    }
}

@Composable
private fun RowAction(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .heightIn(min = 24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** The game calls the planted form a Crystal and the inventory form a Shard. */
private fun crystalName(itemName: String?, type: CrystalType): String =
    itemName?.removeSuffix(" Shard")?.plus(" Crystal") ?: type.id

private fun shardEffect(type: CrystalType): String = when (type) {
    CrystalType.Hunger -> "pets lose hunger 10% slower"
    CrystalType.XP -> "pets gain XP 10% faster"
    CrystalType.Strength -> "pets gain +10 strength"
    else -> "blocks ${type.blockedMutation} nearby"
}

private fun effectsSummary(hungerRate: Double, xpRate: Double, strengthBonus: Int): String =
    buildList {
        if (hungerRate < 1.0) add("hunger ${((1 - hungerRate) * 100).toInt()}% slower")
        if (xpRate > 1.0) add("XP ${((xpRate - 1) * 100).toInt()}% faster")
        if (strengthBonus > 0) add("+$strengthBonus strength")
    }.joinToString(", ", prefix = "Active on pets: ")

/** `3h 12m` above an hour, `4m 12s` below it. */
internal fun formatDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes.toString().padStart(2, '0')}m"
    else "${minutes}m ${(total % 60).toString().padStart(2, '0')}s"
}
