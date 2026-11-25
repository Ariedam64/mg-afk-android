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
import com.mgafk.app.data.repository.CropCleansers
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.AppTheme

/**
 * A "Cleanse" button opening a drop-down of the held cleansers (CropCleanser, HydroCleanser,
 * LunarCleanser), shared by the single crop and multi-slot plant dialogs. A cleanser is enabled
 * only when the crop carries a mutation of the groups it clears.
 */
@Composable
internal fun CropCleanserPicker(
    cleansers: List<CropCleansers.Offer>,
    cropMutations: List<String>,
    onCleanse: (toolId: String) -> Unit,
    modifier: Modifier = Modifier,
    buttonHeight: Dp? = null,
    fontSize: TextUnit = 12.sp,
    compact: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val state = CropCleansers.pickerState(
        offers = cleansers,
        cropMutations = cropMutations,
        mutations = { MgApi.getMutations()[it] },
        displayName = { MgApi.itemDisplayName(it) },
    )
    val label = if (compact) {
        when (state.label) {
            "No mutations" -> "No muts"
            "No cleansers" -> "No tool"
            else -> state.label
        }
    } else state.label
    val buttonModifier = if (buttonHeight != null) {
        Modifier.fillMaxWidth().height(buttonHeight)
    } else {
        Modifier.fillMaxWidth()
    }

    Box(modifier = modifier) {
        Button(
            onClick = { menuOpen = true },
            enabled = state.enabled,
            modifier = buttonModifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF8B5CF6),
                disabledContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                disabledContentColor = Color.White.copy(alpha = 0.4f),
            ),
            shape = AppTheme.shapes.smallShape,
            contentPadding = if (compact) PaddingValues(0.dp) else ButtonDefaults.ContentPadding,
        ) {
            Text(
                label,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = if (state.enabled) Color.White else Color.White.copy(alpha = 0.4f),
            )
        }
        DropdownMenu(
            expanded = menuOpen && state.enabled,
            onDismissRequest = { menuOpen = false },
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
                        menuOpen = false
                        onCleanse(item.offer.toolId)
                    },
                )
            }
        }
    }
}
