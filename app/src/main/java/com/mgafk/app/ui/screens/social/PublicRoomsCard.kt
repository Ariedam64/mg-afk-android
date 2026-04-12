package com.mgafk.app.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mgafk.app.data.repository.AriesApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted

@Composable
fun PublicRoomsCard(
    rooms: List<AriesApi.PublicRoom>,
    loading: Boolean,
    currentRoomId: String,
    isConnected: Boolean,
    onRefresh: () -> Unit,
    onJoin: (roomId: String) -> Unit,
) {
    // Always refresh when section is displayed
    LaunchedEffect(Unit) {
        onRefresh()
    }

    // Refresh when connection state changes (just connected to new room)
    LaunchedEffect(isConnected, currentRoomId) {
        if (isConnected) onRefresh()
    }

    // Sort: current room always first (even if full), then non-full descending by player count
    val sortedRooms = remember(rooms, currentRoomId) {
        val current = rooms.filter { it.id.equals(currentRoomId, ignoreCase = true) }
        val others = rooms.filter { !it.id.equals(currentRoomId, ignoreCase = true) && it.playersCount < 6 }
            .sortedByDescending { it.playersCount }
        current + others
    }

    // Track which room is being joined — clear when connected
    var joiningRoomId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isConnected, currentRoomId) {
        if (isConnected && joiningRoomId != null) {
            joiningRoomId = null
        }
    }

    AppCard(
        title = "Public Rooms",
        collapsible = true,
        persistKey = "social.publicRooms",
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = Accent,
                    )
                } else {
                    Text(
                        "${sortedRooms.size} rooms",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Accent.copy(alpha = 0.7f),
                    )
                }
                Text(
                    "Refresh",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (loading) Accent.copy(alpha = 0.4f) else Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { if (!loading) onRefresh() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        },
    ) {
        if (rooms.isEmpty() && !loading) {
            Text("No public rooms available.", fontSize = 12.sp, color = TextMuted)
        } else {
            // Content with blur overlay when loading
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .then(if (loading) Modifier.blur(4.dp) else Modifier),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (sortedRooms.isEmpty() && loading) {
                        // Placeholder cards while loading
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceDark),
                            )
                        }
                    } else {
                        sortedRooms.forEach { room ->
                            RoomCard(
                                room = room,
                                isCurrent = room.id.equals(currentRoomId, ignoreCase = true),
                                isConnected = isConnected,
                                isJoining = room.id == joiningRoomId,
                                onJoin = {
                                    joiningRoomId = room.id
                                    onJoin(room.id)
                                },
                            )
                        }
                    }
                }

                // Loading overlay
                if (loading) {
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                            color = Accent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomCard(
    room: AriesApi.PublicRoom,
    isCurrent: Boolean,
    isConnected: Boolean,
    isJoining: Boolean,
    onJoin: () -> Unit,
) {
    val countColor = when {
        room.playersCount >= 5 -> Color(0xFFFBBF24)
        room.playersCount >= 3 -> Accent
        else -> Accent.copy(alpha = 0.7f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .then(
                if (isCurrent) Modifier.border(1.5.dp, StatusConnected.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatars — bigger, properly round, nicely spaced
        Row(
            horizontalArrangement = Arrangement.spacedBy((-8).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            room.players.take(6).forEach { player ->
                if (player.avatarUrl != null) {
                    AsyncImage(
                        model = player.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .aspectRatio(1f)
                            .border(2.dp, SurfaceDark, CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .aspectRatio(1f)
                            .background(Accent.copy(alpha = 0.2f))
                            .border(2.dp, SurfaceDark, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            player.name.take(1).uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Accent,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Player count + slots
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${room.playersCount}/6",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = countColor,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Slots dots
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(6) { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (i < room.playersCount) countColor.copy(alpha = 0.8f)
                                else TextMuted.copy(alpha = 0.2f)
                            ),
                    )
                }
            }
        }

        // Join button / Joining spinner / Connected label
        if (isJoining) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Accent,
            )
        } else if (!isCurrent) {
            Button(
                onClick = onJoin,
                modifier = Modifier.height(36.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text("Join", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(StatusConnected.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Connected", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusConnected)
            }
        }
    }
}
