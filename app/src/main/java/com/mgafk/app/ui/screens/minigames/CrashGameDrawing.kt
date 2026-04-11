package com.mgafk.app.ui.screens.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary

// ── Rocket drawing ──

internal fun DrawScope.drawRocket(
    cx: Float,
    cy: Float,
    tilt: Float,
    cashedOut: Boolean,
    multiplier: Double,
) {
    rotate(degrees = tilt, pivot = Offset(cx, cy)) {
        val bodyWidth = 70f
        val bodyHeight = 120f
        val noseHeight = 35f

        val bodyColor = when {
            multiplier >= 10.0 -> Color(0xFFFF4444)
            multiplier >= 5.0 -> Color(0xFFFFA500)
            else -> Color(0xFFE8ECF0)
        }
        val accentColor = when {
            multiplier >= 10.0 -> Color(0xFFCC0000)
            multiplier >= 5.0 -> Color(0xFFCC7700)
            else -> Color(0xFF6C8CFF)
        }

        val bodyTop = cy - bodyHeight / 2 + noseHeight / 2

        // Body (rounded rectangle)
        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(cx - bodyWidth / 2, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(12f, 12f),
        )

        // Nose cone (triangle)
        val nosePath = Path().apply {
            moveTo(cx, cy - bodyHeight / 2 - noseHeight / 2 + 3)
            lineTo(cx - bodyWidth / 2, bodyTop)
            lineTo(cx + bodyWidth / 2, bodyTop)
            close()
        }
        drawPath(nosePath, accentColor)

        // Porthole window (circle)
        val portholeY = bodyTop + 35f
        drawCircle(
            color = Color(0xFF1A2940),
            radius = 20f,
            center = Offset(cx, portholeY),
        )
        drawCircle(
            color = accentColor.copy(alpha = 0.5f),
            radius = 20f,
            center = Offset(cx, portholeY),
            style = Stroke(width = 2f),
        )
        // Window shine
        drawCircle(
            color = Color.White.copy(alpha = 0.15f),
            radius = 6f,
            center = Offset(cx - 6f, portholeY - 7f),
        )

        // Side fins
        val finBottom = cy + bodyHeight / 2 + noseHeight / 2
        // Left fin
        val leftFin = Path().apply {
            moveTo(cx - bodyWidth / 2, finBottom - 30f)
            lineTo(cx - bodyWidth / 2 - 22f, finBottom + 9f)
            lineTo(cx - bodyWidth / 2, finBottom)
            close()
        }
        drawPath(leftFin, accentColor)
        // Right fin
        val rightFin = Path().apply {
            moveTo(cx + bodyWidth / 2, finBottom - 30f)
            lineTo(cx + bodyWidth / 2 + 22f, finBottom + 9f)
            lineTo(cx + bodyWidth / 2, finBottom)
            close()
        }
        drawPath(rightFin, accentColor)

        // Stripe on body
        drawRoundRect(
            color = accentColor.copy(alpha = 0.3f),
            topLeft = Offset(cx - bodyWidth / 2 + 8f, cy + 15f),
            size = Size(bodyWidth - 16f, 7f),
            cornerRadius = CornerRadius(3f, 3f),
        )

        // Second stripe
        drawRoundRect(
            color = accentColor.copy(alpha = 0.2f),
            topLeft = Offset(cx - bodyWidth / 2 + 8f, cy + 27f),
            size = Size(bodyWidth - 16f, 5f),
            cornerRadius = CornerRadius(2f, 2f),
        )

        // Cashout glow
        if (cashedOut) {
            drawCircle(StatusConnected.copy(alpha = 0.3f), radius = 40f, center = Offset(cx, cy))
        }
    }
}

internal fun DrawScope.drawRocketFlame(
    cx: Float,
    topY: Float,
    flameHeight: Float,
    multiplier: Double,
) {
    val flameColor1 = when {
        multiplier >= 10.0 -> Color(0xFFFF0000)
        multiplier >= 5.0 -> Color(0xFFFF6B00)
        else -> Color(0xFFFF8C00)
    }
    val flameColor2 = when {
        multiplier >= 10.0 -> Color(0xFFFF4444)
        multiplier >= 5.0 -> Color(0xFFFFAA00)
        else -> Color(0xFFFFD700)
    }

    // Outer flame
    val outerFlame = Path().apply {
        moveTo(cx - 22f, topY)
        lineTo(cx, topY + flameHeight)
        lineTo(cx + 22f, topY)
        close()
    }
    drawPath(
        outerFlame,
        Brush.verticalGradient(
            colors = listOf(flameColor1.copy(alpha = 0.8f), flameColor2.copy(alpha = 0.2f)),
            startY = topY,
            endY = topY + flameHeight,
        ),
    )

    // Inner flame (brighter, shorter)
    val innerFlame = Path().apply {
        moveTo(cx - 11f, topY)
        lineTo(cx, topY + flameHeight * 0.6f)
        lineTo(cx + 11f, topY)
        close()
    }
    drawPath(
        innerFlame,
        Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.9f), flameColor2.copy(alpha = 0.3f)),
            startY = topY,
            endY = topY + flameHeight * 0.6f,
        ),
    )
}

@Composable
internal fun TimeChip(time: String, mult: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(time, fontSize = 10.sp, color = TextMuted)
        Text(mult, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
    }
}
