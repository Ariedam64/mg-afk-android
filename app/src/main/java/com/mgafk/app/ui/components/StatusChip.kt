package com.mgafk.app.ui.components

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.StatusIdle

@Composable
fun StatusChip(status: SessionStatus, modifier: Modifier = Modifier) {
    val (targetColor, label) = when (status) {
        SessionStatus.CONNECTED -> StatusConnected to "Connected"
        SessionStatus.CONNECTING -> StatusConnecting to "Connecting"
        SessionStatus.ERROR -> StatusError to "Error"
        SessionStatus.IDLE -> StatusIdle to "Offline"
    }
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "statusColor",
    )

    Row(
        modifier = modifier
            .clip(AppTheme.shapes.largeShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
