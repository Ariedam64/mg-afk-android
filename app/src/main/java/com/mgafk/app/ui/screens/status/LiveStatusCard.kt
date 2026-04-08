package com.mgafk.app.ui.screens.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.TextPrimary

private val WEATHER_API_KEYS = mapOf(
    "Clear Skies" to "Sunny",
    "Rain" to "Rain",
    "Snow" to "Frost",
    "Amber Moon" to "AmberMoon",
    "Dawn" to "Dawn",
    "Thunderstorm" to "Thunderstorm",
)

@Composable
fun LiveStatusCard(session: Session, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, title = "Live Status", collapsible = true) {
        StatusRow("Players", "${session.players}")
        StatusRow("Uptime", session.uptime, mono = true)
        StatusRow("Player", session.playerName.ifBlank { "-" })
        StatusRow("Room ID", session.roomId.ifBlank { "-" })
        WeatherRow(session.weather)
        StatusRow("Player ID", session.playerId.ifBlank { "-" })
    }
}

@Composable
private fun StatusRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = if (mono) Accent else TextPrimary,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
private fun WeatherRow(weather: String) {
    val spriteUrl = if (weather.isBlank()) null
        else MgApi.weatherInfo(WEATHER_API_KEYS[weather] ?: weather)?.sprite

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Weather",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (spriteUrl != null) {
                SpriteImage(url = spriteUrl, size = 18.dp, contentDescription = weather)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(text = weather.ifBlank { "-" }, fontSize = 12.sp)
        }
    }
}
