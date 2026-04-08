package com.mgafk.app.ui.screens.pets

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.websocket.Constants
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.TextMuted

@Composable
fun PetHungerCard(pets: List<PetSnapshot>, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, title = "Pet Hunger", collapsible = true) {
        if (pets.isEmpty()) {
            Text("No pets found.", fontSize = 12.sp, color = TextMuted)
        } else {
            pets.forEachIndexed { i, pet ->
                PetRow(pet)
                if (i < pets.lastIndex) Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PetRow(pet: PetSnapshot) {
    val maxHunger = Constants.PET_HUNGER_COSTS[pet.species.lowercase()] ?: 1000
    val percent = ((pet.hunger.toFloat() / maxHunger) * 100).coerceIn(0f, 100f)
    val color = when {
        percent < 5f -> StatusError
        percent < 25f -> Color(0xFFFBBF24)
        else -> StatusConnected
    }
    val mutationLabel = if (pet.mutations.isNotEmpty()) pet.mutations.joinToString(", ") else null

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpriteImage(category = "pets", name = pet.species, size = 28.dp, contentDescription = pet.species)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pet.name.ifBlank { pet.species },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (mutationLabel != null) {
                        Text(" · $mutationLabel", fontSize = 11.sp, color = Color(0xFFEBC800))
                    }
                }
                Text(
                    text = "%.1f%%".format(percent),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (percent / 100f).coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(color),
                )
            }
        }
    }
}
