package com.mgafk.app.ui.screens.garden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.MutationPotions
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.AppTheme

/** One row of the potion drop-down menu: the offer it sends, its label, and whether it can be chosen. */
internal data class MutationPotionMenuItem(
    val offer: MutationPotions.Offer,
    val label: String,
    val enabled: Boolean,
)

/** What the shared Potion button shows: enabled or not, its label, and the menu rows in order. */
internal data class MutationPotionPickerState(
    val enabled: Boolean,
    val buttonLabel: String,
    val items: List<MutationPotionMenuItem>,
)

/** Pure decision behind [MutationPotionPicker]; keeps the order of [potions]. */
internal fun mutationPotionPickerState(
    potions: List<MutationPotions.Offer>,
    nameOf: (String) -> String?,
): MutationPotionPickerState {
    val hasPotions = potions.isNotEmpty()
    return MutationPotionPickerState(
        enabled = hasPotions,
        buttonLabel = if (hasPotions) "Potion" else "No potion",
        items = potions.map { potion ->
            MutationPotionMenuItem(
                offer = potion,
                label = mutationPotionLabel(potion, nameOf(potion.toolId) ?: potion.toolId),
                enabled = isMutationPotionUsable(potion),
            )
        },
    )
}

/**
 * A single "Potion" button opening a drop-down of the held and Tool Shack mutation potions,
 * shared by the single crop and multi-slot plant dialogs. The server decides whether it applies.
 */
@Composable
internal fun MutationPotionPicker(
    potions: List<MutationPotions.Offer>,
    onApplyPotion: (MutationPotions.Offer) -> Unit,
    modifier: Modifier = Modifier,
    buttonHeight: Dp? = null,
    fontSize: TextUnit = 12.sp,
    compact: Boolean = false,
) {
    var potionMenuOpen by remember { mutableStateOf(false) }
    val state = mutationPotionPickerState(potions) { MgApi.findItem(it)?.name }
    val buttonModifier = if (buttonHeight != null) {
        Modifier.fillMaxWidth().height(buttonHeight)
    } else {
        Modifier.fillMaxWidth()
    }

    Box(modifier = modifier) {
        Button(
            onClick = { potionMenuOpen = true },
            enabled = state.enabled,
            modifier = buttonModifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0EA5E9),
                disabledContainerColor = Color(0xFF0EA5E9).copy(alpha = 0.2f),
                disabledContentColor = Color.White.copy(alpha = 0.4f),
            ),
            shape = AppTheme.shapes.smallShape,
            contentPadding = if (compact) PaddingValues(0.dp) else ButtonDefaults.ContentPadding,
        ) {
            Text(
                state.buttonLabel,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = if (state.enabled) Color.White else Color.White.copy(alpha = 0.4f),
            )
        }
        DropdownMenu(
            expanded = potionMenuOpen && state.enabled,
            onDismissRequest = { potionMenuOpen = false },
        ) {
            state.items.forEach { item ->
                val sprite = MgApi.findItem(item.offer.toolId)?.sprite
                DropdownMenuItem(
                    enabled = item.enabled,
                    text = { Text(item.label, fontSize = 13.sp) },
                    leadingIcon = {
                        SpriteImage(url = sprite, size = 18.dp, contentDescription = item.offer.toolId)
                    },
                    onClick = {
                        potionMenuOpen = false
                        onApplyPotion(item.offer)
                    },
                )
            }
        }
    }
}
