package com.mgafk.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.DashboardCard
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.model.ShopSnapshot
import com.mgafk.app.data.model.WeatherForecast
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.websocket.Constants
import com.mgafk.app.ui.MainViewModel
import com.mgafk.app.ui.UiState
import com.mgafk.app.ui.components.CardStack
import com.mgafk.app.ui.components.PillButton
import com.mgafk.app.ui.components.ProgressRing
import com.mgafk.app.ui.components.SectionHeader
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.components.TonalCard
import com.mgafk.app.ui.components.formatClock
import com.mgafk.app.ui.components.formatCountdown
import com.mgafk.app.ui.components.levelColor
import com.mgafk.app.ui.navigation.Destination
import com.mgafk.app.ui.screens.connection.ConnectionCard
import com.mgafk.app.ui.screens.status.LiveStatusCard
import com.mgafk.app.ui.theme.AppTheme
import kotlinx.coroutines.delay

/** One clock for every countdown on Home, so they never disagree by a frame. */
@Composable
private fun rememberNowMs(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return now
}

@Composable
fun HomeScreen(
    state: UiState,
    viewModel: MainViewModel,
    onNavigate: (Destination) -> Unit,
    onLoginRequest: (sessionId: String) -> Unit,
    onPlayRequest: (sessionId: String, cookie: String, room: String, gameUrl: String) -> Unit,
) {
    val session = state.activeSession
    val hidden = state.settings.appearance.hiddenDashboardCards
    val nowMs = rememberNowMs()

    CardStack {
        AccountHeader(
            sessions = state.sessions,
            active = session,
            onSelect = viewModel::selectSession,
            onAdd = viewModel::addSession,
            onRemove = { viewModel.removeSession(session.id) },
        )

        // The connection form stays up front until the session is live; once connected it
        // moves below the useful cards, where Disconnect is still one scroll away.
        val connectionCard: @Composable () -> Unit = {
            ConnectionCard(
                session = session,
                onCookieChange = { cookie -> viewModel.updateSession(session.id) { it.copy(cookie = cookie) } },
                onRoomChange = { room -> viewModel.updateSession(session.id) { it.copy(room = room) } },
                onConnect = { viewModel.connect(session.id) },
                onDisconnect = { viewModel.disconnect(session.id) },
                onLogin = { onLoginRequest(session.id) },
                onLogout = { viewModel.clearToken(session.id) },
            )
        }
        if (!session.connected) connectionCard()

        if (DashboardCard.WEATHER !in hidden) {
            WeatherHero(forecast = state.weatherForecast, nowMs = nowMs)
        }

        PlayInGame(
            canPlay = session.cookie.isNotBlank() && session.room.isNotBlank(),
            injectGemini = state.settings.injectGeminiMod,
            onToggleInjectGemini = { enabled -> viewModel.updateSettings { it.copy(injectGeminiMod = enabled) } },
            onPlay = { onPlayRequest(session.id, session.cookie, session.room, session.gameUrl) },
        )

        if (session.connected && DashboardCard.PETS !in hidden && session.pets.isNotEmpty()) {
            SectionHeader("Your pets", action = "See all", onAction = { onNavigate(Destination.PETS) })
            PetRings(pets = session.pets, onClick = { onNavigate(Destination.PETS) })
        }

        if (session.connected && DashboardCard.SHOPS !in hidden && session.shops.isNotEmpty()) {
            SectionHeader("Shops restock", action = "Open", onAction = { onNavigate(Destination.SHOPS) })
            ShopRestockGrid(shops = session.shops, nowMs = nowMs, onClick = { onNavigate(Destination.SHOPS) })
        }

        if (session.connected && DashboardCard.STATUS !in hidden) {
            LiveStatusCard(session = session)
        }

        if (session.connected) connectionCard()
    }
}

// ── Weather ──

@Composable
private fun WeatherHero(forecast: WeatherForecast?, nowMs: Long) {
    val colors = AppTheme.colors
    TonalCard(color = colors.hero, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (forecast == null || (forecast.now == null && forecast.upcoming.isEmpty())) {
            Text("Weather now", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
            Text("Forecast unavailable", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            return@TonalCard
        }
        val now = forecast.now
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Weather now", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                Text(
                    now?.label ?: "Clear Skies",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    lineHeight = 36.sp,
                )
                val endsIn = now?.endsInMs(nowMs) ?: 0L
                if (endsIn > 0) {
                    Text("Ends in ${formatCountdown(endsIn)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary)
                }
            }
            if (now?.spriteUrl != null) {
                SpriteImage(url = now.spriteUrl, size = 64.dp, contentDescription = null)
            } else {
                Icon(Icons.Rounded.Cloud, contentDescription = null, tint = colors.accent, modifier = Modifier.size(56.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            forecast.nextHydro(nowMs)?.let { hydro ->
                ForecastChip(hydro.label, formatCountdown(hydro.startsInMs(nowMs)))
            }
            // The game keeps Dawn and Amber Moon a surprise until they start: timing only.
            forecast.nextLunar(nowMs)?.let { lunar ->
                ForecastChip("Lunar event", formatCountdown(lunar.startsInMs(nowMs)))
            }
        }
    }
}

@Composable
private fun ForecastChip(label: String, countdown: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(AppTheme.colors.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(countdown, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textMuted)
    }
}

// ── Play ──

@Composable
private fun PlayInGame(
    canPlay: Boolean,
    injectGemini: Boolean,
    onToggleInjectGemini: (Boolean) -> Unit,
    onPlay: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PillButton(
            text = if (canPlay) "Play in game" else "Set cookie and room first",
            onClick = onPlay,
            enabled = canPlay,
            trailingIcon = if (canPlay) Icons.AutoMirrored.Rounded.ArrowForward else null,
            modifier = Modifier.fillMaxWidth(),
            height = 54.dp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable(role = Role.Switch) { onToggleInjectGemini(!injectGemini) }
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (injectGemini) "With the Gemini mod" else "Vanilla game, no mod",
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                color = AppTheme.colors.textSecondary,
            )
            Switch(
                checked = injectGemini,
                onCheckedChange = onToggleInjectGemini,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = AppTheme.colors.accent,
                    checkedThumbColor = AppTheme.colors.onAccent,
                    uncheckedTrackColor = AppTheme.colors.surfaceHigh,
                    uncheckedBorderColor = AppTheme.colors.outline,
                ),
            )
        }
    }
}

// ── Pets ──

@Composable
private fun PetRings(pets: List<PetSnapshot>, onClick: () -> Unit) {
    // Three per row: one row for a full team, a second only for a bigger roster.
    pets.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.item)) {
            row.forEach { pet -> PetRing(pet, onClick, Modifier.weight(1f)) }
            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PetRing(pet: PetSnapshot, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val maxHunger = Constants.maxHungerFor(pet.species) ?: 1000
    val percent = (pet.hunger / maxHunger * 100).toFloat().coerceIn(0f, 100f)
    val color = levelColor(percent)
    val name = pet.name.ifBlank { MgApi.findPet(pet.species)?.name ?: pet.species }
    TonalCard(
        modifier = modifier,
        onClick = onClick,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgressRing(fraction = percent / 100f, color = color, size = 60.dp) {
                SpriteImage("pets", pet.species, size = 34.dp, mutations = pet.mutations)
            }
            Text(
                name,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text("${percent.toInt()}% fed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

// ── Shops ──

private val CORE_SHOPS = listOf("seed" to "Seeds", "egg" to "Eggs", "tool" to "Tools", "decor" to "Decor")

@Composable
private fun ShopRestockGrid(shops: List<ShopSnapshot>, nowMs: Long, onClick: () -> Unit) {
    val tiles = CORE_SHOPS.mapNotNull { (type, label) -> shops.find { it.type == type }?.let { label to it } }
    tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.item)) {
            row.forEach { (label, shop) ->
                ShopTile(label, shop.secondsUntilRestock(nowMs), onClick, Modifier.weight(1f))
            }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ShopTile(label: String, seconds: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    // The last minute is when it is worth opening the app.
    val soon = seconds in 1..60
    Row(
        modifier = modifier
            .clip(AppTheme.shapes.mediumShape)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (soon) colors.accent else colors.surfaceHigh))
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            if (seconds > 0) formatClock(seconds) else "-",
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (soon) colors.accent else colors.textPrimary,
        )
    }
}
