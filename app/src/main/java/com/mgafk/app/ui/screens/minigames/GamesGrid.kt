package com.mgafk.app.ui.screens.minigames

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary

data class GameDef(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val available: Boolean = false,
    val imageUrl: String? = null,
)

private val GAMES = listOf(
    GameDef("coinflip", "Coin Flip", "\uD83E\uDE99", "Double or nothing", available = true, imageUrl = "https://i.imgur.com/J2gqn25.png"),
    GameDef("mines", "Mines", "\uD83D\uDCA3", "Avoid the mines", available = true, imageUrl = "https://mg-api.ariedam.fr/assets/sprites/ui/Locked.png"),
    GameDef("slots", "Slots", "\uD83C\uDFB0", "Spin to win", available = true),
    GameDef("dice", "Dice", "\uD83C\uDFB2", "Roll over or under", available = true),
    GameDef("crash", "Crash", "\uD83D\uDCC8", "Cashout before crash", available = true),
    GameDef("blackjack", "Blackjack", "\uD83C\uDCA1", "Beat the dealer", available = true),
)

@Composable
fun GamesGrid(
    modifier: Modifier = Modifier,
    onGameClick: (String) -> Unit = {},
) {
    AppCard(modifier = modifier, title = "Games") {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .height(((GAMES.size + 2) / 3 * 120).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(GAMES, key = { it.id }) { game ->
                GameTile(game = game, onClick = { onGameClick(game.id) })
            }
        }
    }
}

@Composable
private fun GameTile(
    game: GameDef,
    onClick: () -> Unit,
) {
    val borderColor = if (game.available) Accent.copy(alpha = 0.3f) else SurfaceBorder
    val bgColor = if (game.available) Accent.copy(alpha = 0.05f) else SurfaceDark

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = game.available, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (game.imageUrl != null) {
            AsyncImage(
                model = game.imageUrl,
                contentDescription = game.name,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
        } else {
            Text(
                text = game.emoji,
                fontSize = 28.sp,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = game.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (game.available) TextPrimary else TextMuted,
            textAlign = TextAlign.Center,
        )
        if (!game.available) {
            Spacer(modifier = Modifier.height(2.dp))
            Text("Coming soon", fontSize = 10.sp, color = TextMuted, textAlign = TextAlign.Center)
        }
    }
}
