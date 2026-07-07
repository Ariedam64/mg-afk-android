package com.mgafk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.theme.rarityColor

/**
 * Horizontal "All" + per-rarity icon filter chips for item grids that list every known
 * species/id, which gets long fast. Rarity chips show the game's own rarity badge sprite
 * (compact, no label) instead of the full tier name. [rarities] should only include tiers
 * actually present among the caller's current items, in display order.
 */
@Composable
fun RarityFilterRow(
    rarities: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AllFilterChip(selected = selected == null, onClick = { onSelect(null) })
        rarities.forEach { rarity ->
            RarityFilterChip(rarity = rarity, selected = selected == rarity, onClick = { onSelect(rarity) })
        }
    }
}

@Composable
private fun AllFilterChip(selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) Accent else SurfaceBorder
    val bgColor = if (selected) Accent.copy(alpha = 0.15f) else SurfaceDark
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "All",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Accent else TextSecondary,
        )
    }
}

/** Compact circular chip showing only the rarity's badge icon (name would take too much space). */
@Composable
private fun RarityFilterChip(rarity: String, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) Accent else rarityColor(rarity).copy(alpha = 0.6f)
    val bgColor = if (selected) Accent.copy(alpha = 0.15f) else SurfaceDark
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(1.5.dp, borderColor, CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SpriteImage(url = MgApi.raritySpriteUrl(rarity), size = 20.dp, contentDescription = rarity)
    }
}
