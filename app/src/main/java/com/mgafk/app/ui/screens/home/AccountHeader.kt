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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.ui.components.StatusPill
import com.mgafk.app.ui.theme.AppTheme

/**
 * The top of Home: who is playing, where, and whether it is live. Tapping the name opens
 * the account switcher, which replaces the old row of session chips.
 */
@Composable
fun AccountHeader(
    sessions: List<Session>,
    active: Session,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = AppTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            val initial = active.playerName.ifBlank { active.name }.firstOrNull()?.uppercaseChar() ?: 'M'
            Text(initial.toString(), color = colors.onAccent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }

        Box(Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.DropdownList) { menuOpen = true }
                    .padding(vertical = 2.dp, horizontal = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        active.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = "Switch account", tint = colors.textSecondary)
                }
                Text(
                    subtitle(active),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = colors.surfaceHigh,
                shape = AppTheme.shapes.mediumShape,
            ) {
                sessions.forEach { s ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                s.name,
                                fontWeight = if (s.id == active.id) FontWeight.ExtraBold else FontWeight.Medium,
                                color = colors.textPrimary,
                            )
                        },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(s.status))) },
                        onClick = {
                            menuOpen = false
                            onSelect(s.id)
                        },
                    )
                }
                HorizontalDivider(color = colors.outline)
                DropdownMenuItem(
                    text = { Text("Add account", color = colors.textPrimary) },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accent) },
                    onClick = {
                        menuOpen = false
                        onAdd()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove ${active.name}", color = colors.bad) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = colors.bad) },
                    onClick = {
                        menuOpen = false
                        confirmRemove = true
                    },
                )
            }
        }

        StatusPill(text = statusLabel(active.status), color = statusColor(active.status))
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            containerColor = colors.surfaceHigh,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("Remove account") },
            text = { Text("Remove \"${active.name}\"? This disconnects it and deletes its data.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text("Remove", color = colors.bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel", color = colors.textSecondary) }
            },
        )
    }
}

private fun subtitle(session: Session): String = when (session.status) {
    SessionStatus.CONNECTED -> {
        val room = session.roomId.ifBlank { session.room }
        val players = if (session.players == 1) "1 player" else "${session.players} players"
        if (room.isBlank()) players else "Room $room · $players"
    }
    SessionStatus.CONNECTING -> session.reconnectCountdown.ifBlank { "Connecting" }
    SessionStatus.ERROR -> session.error.ifBlank { "Connection error" }
    SessionStatus.IDLE -> if (session.cookie.isBlank()) "Not logged in" else "Offline"
}

private fun statusLabel(status: SessionStatus) = when (status) {
    SessionStatus.CONNECTED -> "Online"
    SessionStatus.CONNECTING -> "Connecting"
    SessionStatus.ERROR -> "Error"
    SessionStatus.IDLE -> "Offline"
}

@Composable
@ReadOnlyComposable
private fun statusColor(status: SessionStatus): Color = when (status) {
    SessionStatus.CONNECTED -> AppTheme.colors.good
    SessionStatus.CONNECTING -> AppTheme.colors.warn
    SessionStatus.ERROR -> AppTheme.colors.bad
    SessionStatus.IDLE -> AppTheme.colors.idle
}
