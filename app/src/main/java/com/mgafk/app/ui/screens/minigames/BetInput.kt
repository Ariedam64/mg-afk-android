package com.mgafk.app.ui.screens.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary

/**
 * Shared bet amount input used across all mini-games.
 *
 * @param amount Current amount string
 * @param onAmountChange Called when the amount changes
 * @param balance Current casino balance (for Max button)
 * @param maxBet Maximum allowed bet for this game
 * @param label Label for the text field (default "Bet amount")
 */
@Composable
fun BetInput(
    amount: String,
    onAmountChange: (String) -> Unit,
    balance: Long?,
    maxBet: Long = 30_000,
    label: String = "Bet amount",
) {
    val parsed = amount.toLongOrNull() ?: 0L
    val effectiveMax = if (balance != null) minOf(balance, maxBet) else maxBet

    OutlinedTextField(
        value = amount,
        onValueChange = { new -> onAmountChange(new.filter { it.isDigit() }) },
        label = { Text(label) },
        placeholder = { Text("Max ${numberFormat.format(maxBet)}") },
        leadingIcon = {
            AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = SurfaceBorder,
            focusedLabelColor = Accent, cursorColor = Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Presets row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(100L to "100", 500L to "500", 1_000L to "1K", 5_000L to "5K", 10_000L to "10K").forEach { (value, text) ->
            val isSelected = parsed == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Accent.copy(alpha = 0.15f) else Accent.copy(alpha = 0.04f))
                    .border(1.dp, if (isSelected) Accent.copy(alpha = 0.4f) else Accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .clickable { onAmountChange(value.toString()) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isSelected) Accent else TextPrimary)
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Relative buttons row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Min
        RelativeButton("Min", Modifier.weight(1f)) {
            onAmountChange("1")
        }
        // /2
        RelativeButton("/2", Modifier.weight(1f)) {
            val half = (parsed / 2).coerceAtLeast(1)
            onAmountChange(half.toString())
        }
        // x2
        RelativeButton("x2", Modifier.weight(1f)) {
            val doubled = (parsed * 2).coerceAtMost(effectiveMax)
            onAmountChange(doubled.toString())
        }
        // Max
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(StatusConnected.copy(alpha = 0.12f))
                .border(1.dp, StatusConnected.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable { onAmountChange(effectiveMax.toString()) }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Max", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusConnected)
        }
    }
}

@Composable
private fun RelativeButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceBorder.copy(alpha = 0.4f))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}
