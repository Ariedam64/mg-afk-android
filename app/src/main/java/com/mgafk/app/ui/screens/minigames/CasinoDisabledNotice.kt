package com.mgafk.app.ui.screens.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary

/** Quote from the game developer announcing the change that disables the casino. */
private const val DEV_MESSAGE =
    "Bread transfer limit is now 10K per day, with a limit of 10 transfers per day. " +
        "Still plenty for being generous, but stops some automation and abuse we've seen lately.\n\n" +
        "Reminder that bread is soon to be removed as the currency used to purchase cosmetics " +
        "in favor of coins, dust, and donuts\n\n" +
        "(However, we are considering keeping /doughnate around as a fun \"karma\" system for the " +
        "discord, even after cosmetics & minigames move away from bread, since it's fun to give " +
        "people internet points)"

/**
 * Shown in place of the casino / mini games UI. The casino relied on bread transfers,
 * which the game now rate-limits, so the feature is disabled.
 */
@Composable
fun CasinoDisabledNotice() {
    AppCard(title = "Mini Games unavailable") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "The casino and mini games are no longer available.",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "They relied on bread transfers, which the game now rate-limits. Here is the developer's announcement:",
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 16.sp,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            Text(
                DEV_MESSAGE,
                fontSize = 12.sp,
                color = TextMuted,
                lineHeight = 17.sp,
            )
        }
    }
}
