package com.mgafk.app.ui.screens.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.WeatherEvent
import com.mgafk.app.data.model.WeatherForecast
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * The game's Weather Station, as three cards: what is running now, what comes next, and when
 * the next lunar event lands.
 *
 * A lunar event is announced by its timing only, never by its name: the game keeps Dawn and
 * Amber Moon a surprise until they start, and so does this.
 *
 * The countdowns tick locally against the events' absolute timestamps, so a forecast fetched a
 * minute ago still shows the right numbers without re-polling every second.
 */
@Composable
fun WeatherStationCard(
    forecast: WeatherForecast?,
    modifier: Modifier = Modifier,
) {
    AppCard(
        modifier = modifier,
        title = "Weather Station",
        collapsible = true,
        persistKey = "dashboard.weatherStation",
    ) {
        if (forecast?.now == null && forecast?.upcoming.isNullOrEmpty()) {
            Text("Forecast unavailable.", fontSize = 11.sp, color = TextMuted)
            return@AppCard
        }

        // One clock for the three cards, so their countdowns never disagree by a frame.
        var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ForecastCard(
                label = "Now",
                event = forecast.now,
                countdownLabel = "ends in",
                countdownMs = forecast.now?.endsInMs(nowMs),
                highlighted = true,
                modifier = Modifier.weight(1f),
            )
            val next = forecast.next(nowMs)
            ForecastCard(
                label = "Next",
                event = next,
                countdownLabel = "in",
                countdownMs = next?.startsInMs(nowMs),
                // A lunar event keeps its secret here too, exactly like the Lunar card.
                hideIdentity = next?.isLunar == true,
                modifier = Modifier.weight(1f),
            )
            val lunar = forecast.nextLunar(nowMs)
            ForecastCard(
                label = "Lunar",
                event = lunar,
                countdownLabel = "in",
                countdownMs = lunar?.startsInMs(nowMs),
                hideIdentity = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ForecastCard(
    label: String,
    event: WeatherEvent?,
    countdownLabel: String,
    countdownMs: Long?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    /**
     * Show only that a lunar event is coming, never which one. The game keeps Dawn and Amber
     * Moon a surprise until they start, and the mutation name would give it away as surely as
     * the name and the icon do.
     */
    hideIdentity: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) Accent.copy(alpha = 0.10f) else SurfaceDark)
            .border(
                1.dp,
                if (highlighted) Accent.copy(alpha = 0.4f) else SurfaceBorder.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp),
            )
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) Accent else TextMuted,
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (event == null) {
            Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                Text("-", fontSize = 14.sp, color = TextMuted)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("nothing in range", fontSize = 9.sp, color = TextMuted, textAlign = TextAlign.Center)
            return@Column
        }

        if (hideIdentity) {
            LunarPair()
        } else {
            SpriteImage(url = event.spriteUrl, size = 28.dp, contentDescription = event.label)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (hideIdentity) "Lunar" else event.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "$countdownLabel ${formatCountdown(countdownMs ?: 0L)}",
            fontSize = 10.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (!hideIdentity) {
            event.mutation?.let { mutation ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(mutation, fontSize = 9.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Both lunar icons side by side: it will be one of these two, and the game does not say which
 * until it starts.
 */
@Composable
private fun LunarPair() {
    Row(
        modifier = Modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (weatherId in LUNAR_WEATHER_IDS) {
            SpriteImage(
                url = MgApi.weatherInfo(weatherId)?.sprite,
                size = 22.dp,
                contentDescription = weatherId,
            )
        }
    }
}

/** The only two lunar weathers, per the game's Lunar group. */
private val LUNAR_WEATHER_IDS = listOf("Dawn", "AmberMoon")

/** `2h 05m` past an hour, `4m 12s` below it, so the unit that matters is always visible. */
internal fun formatCountdown(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "${hours}h ${minutes.toString().padStart(2, '0')}m"
    else "${minutes}m ${seconds.toString().padStart(2, '0')}s"
}
