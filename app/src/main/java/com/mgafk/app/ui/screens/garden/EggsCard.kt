package com.mgafk.app.ui.screens.garden

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.GardenEggSnapshot
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

// Rarity colors (same as GardenCard)
private val RarityCommon = Color(0xFFE7E7E7)
private val RarityUncommon = Color(0xFF67BD4D)
private val RarityRare = Color(0xFF0071C6)
private val RarityLegendary = Color(0xFFFFC734)
private val RarityMythical = Color(0xFF9944A7)
private val RarityDivine = Color(0xFFFF7835)
private val RarityCelestial = Color(0xFFFF00FF)

private fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "common" -> RarityCommon
    "uncommon" -> RarityUncommon
    "rare" -> RarityRare
    "legendary" -> RarityLegendary
    "mythical", "mythic" -> RarityMythical
    "divine" -> RarityDivine
    "celestial" -> RarityCelestial
    else -> TextMuted
}

@Composable
fun EggsCard(eggs: List<GardenEggSnapshot>, apiReady: Boolean = false) {
    AppCard(
        title = "Eggs",
        trailing = {
            Text(
                text = "${eggs.size} eggs",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Accent.copy(alpha = 0.7f),
            )
        },
        collapsible = true,
    ) {
        if (eggs.isEmpty()) {
            Text("No eggs in the garden.", fontSize = 12.sp, color = TextMuted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                eggs.forEach { egg ->
                    EggRow(egg = egg, apiReady = apiReady)
                }
            }
        }
    }
}

@Composable
private fun EggRow(egg: GardenEggSnapshot, apiReady: Boolean) {
    val entry = remember(egg.eggId, apiReady) { MgApi.findItem(egg.eggId) }
    val displayName = entry?.name ?: egg.eggId
    val spriteUrl = entry?.sprite
    val rarity = entry?.rarity
    val color = rarityColor(rarity)

    val totalMs = (egg.maturedAt - egg.plantedAt).coerceAtLeast(1)

    // Tick every second so the timer & bar update live
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(egg.plantedAt) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val elapsedMs = (now - egg.plantedAt).coerceAtLeast(0)
    val fraction = (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val isHatched = elapsedMs >= totalMs

    // Remaining time
    val remainingMs = (egg.maturedAt - now).coerceAtLeast(0)
    val remainingSec = (remainingMs / 1000).toInt()
    val hours = remainingSec / 3600
    val minutes = (remainingSec % 3600) / 60
    val seconds = remainingSec % 60
    val timeText = if (isHatched) "Ready!" else {
        if (hours > 0) "%dh %02dm %02ds".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Egg sprite
        SpriteImage(url = spriteUrl, size = 36.dp, contentDescription = displayName)

        Spacer(modifier = Modifier.width(12.dp))

        // Info + progress bar
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    text = timeText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = if (isHatched) StatusConnected else Accent.copy(alpha = 0.8f),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.12f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(
                            if (isHatched) StatusConnected.copy(alpha = 0.8f)
                            else color.copy(alpha = 0.7f)
                        ),
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${(fraction * 100).toInt()}%",
                fontSize = 10.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
