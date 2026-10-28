package com.mgafk.app.ui.screens.pets

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.model.XP_POTION_ID
import com.mgafk.app.data.model.XP_POTION_XP
import java.util.Locale
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.SpriteImage

/**
 * Confirmation before spending an XP Potion. Unlike the Hunger Potion this is not a one-tap
 * action: an XP Potion is a Legendary item and the gain is invisible until it lands, so the
 * dialog shows what the pet's strength becomes before anything is consumed.
 */
@Composable
internal fun XpPotionDialog(
    pet: PetSnapshot,
    strength: Int,
    maxStrength: Int,
    apiReady: Boolean,
    inInventory: Int,
    inShack: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(apiReady) { MgApi.findItem(XP_POTION_ID) }
    val name = entry?.name ?: "XP Potion"
    val projectedStrength = remember(pet.species, pet.xp, pet.targetScale, apiReady) {
        calculatePetStrength(pet.species, pet.xp + XP_POTION_XP, pet.targetScale)
    }
    val stockText = when {
        inInventory > 0 && inShack > 0 -> "x$inInventory in inventory, x$inShack in Tool Shack"
        inInventory > 0 -> "x$inInventory in inventory"
        else -> "x$inShack in Tool Shack, retrieved on use"
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(AppTheme.shapes.mediumShape)
                .background(AppTheme.colors.surface)
                .padding(16.dp),
        ) {
            Text(
                "Strengthen ${pet.name.ifBlank { pet.species }}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary,
            )
            Text(
                "One $name grants ${formatXp(XP_POTION_XP)} XP.",
                fontSize = 11.sp,
                color = AppTheme.colors.textMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppTheme.shapes.smallShape)
                    .border(1.5.dp, AppTheme.colors.accent.copy(alpha = 0.5f), AppTheme.shapes.smallShape)
                    .background(AppTheme.colors.accent.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SpriteImage(url = entry?.sprite, size = 28.dp, contentDescription = name)
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary)
                    Text(stockText, fontSize = 10.sp, color = AppTheme.colors.textMuted, lineHeight = 13.sp)
                }
                if (maxStrength > 0) {
                    Text(
                        "STR $strength -> $projectedStrength",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (projectedStrength > strength) AppTheme.colors.good else AppTheme.colors.textMuted,
                    )
                }
            }

            // Strength moves in whole points, so a potion can be worth real XP and still not
            // shift the number. Better to say so than to let it look like nothing happened.
            if (maxStrength > 0 && projectedStrength == strength) {
                Text(
                    "This potion adds XP but not enough for the next STR point.",
                    fontSize = 10.sp,
                    color = AppTheme.colors.textMuted,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.surfaceHigh),
                ) {
                    Text("Cancel", fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.accent),
                ) {
                    Text("Use", fontSize = 12.sp, color = AppTheme.colors.onAccent)
                }
            }
        }
    }
}

/** `20,000`, so a five-figure XP amount stays readable at a glance. */
private fun formatXp(xp: Int): String = "%,d".format(Locale.US, xp)
