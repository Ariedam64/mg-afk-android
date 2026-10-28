package com.mgafk.app.ui.screens.more

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.BuildConfig
import com.mgafk.app.data.repository.AppRelease
import com.mgafk.app.ui.components.CardStack
import com.mgafk.app.ui.components.PillButton
import com.mgafk.app.ui.components.PillStyle
import com.mgafk.app.ui.components.TonalCard
import com.mgafk.app.ui.navigation.Destination
import com.mgafk.app.ui.theme.AppTheme

/** Everything that does not fit in the bottom bar. */
@Composable
fun MoreScreen(
    connected: Boolean,
    showDebug: Boolean,
    updateAvailable: AppRelease?,
    onNavigate: (Destination) -> Unit,
) {
    val context = LocalContext.current
    val items = Destination.moreItems.filter { it != Destination.DEBUG || showDebug }

    CardStack {
        TonalCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.Top) {
            items.forEach { destination ->
                val enabled = connected || !destination.requiresConnection
                MoreRow(destination, enabled) { onNavigate(destination) }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("MG AFK v${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = AppTheme.colors.textMuted)
            if (updateAvailable != null) {
                PillButton(
                    text = "Download ${updateAvailable.tagName}",
                    style = PillStyle.TONAL,
                    height = 44.dp,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateAvailable.downloadUrl)))
                    },
                )
            }
        }
    }
}

@Composable
private fun MoreRow(destination: Destination, enabled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = AppTheme.spacing.card, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(destination.icon, contentDescription = null, tint = colors.onAccentContainer, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(destination.label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val hint = if (!enabled) "Connect first" else destination.description
            if (hint.isNotBlank()) Text(hint, fontSize = 13.sp, color = colors.textMuted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textMuted)
    }
}
