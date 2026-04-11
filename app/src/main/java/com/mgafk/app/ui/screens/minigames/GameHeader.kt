package com.mgafk.app.ui.screens.minigames

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.TextPrimary

/**
 * Shared header row for all mini-game screens.
 * Shows a back arrow, game title, and the current casino balance.
 */
@Composable
internal fun GameHeader(
    title: String,
    casinoBalance: Long?,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.weight(1f))
        if (casinoBalance != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = BREAD_SPRITE_URL, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    numberFormat.format(casinoBalance),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace, color = StatusConnected,
                )
            }
        }
    }
}
