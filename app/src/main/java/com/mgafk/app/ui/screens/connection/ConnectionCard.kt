package com.mgafk.app.ui.screens.connection

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.Session
import com.mgafk.app.data.model.SessionStatus
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.StatusChip

@Composable
fun ConnectionCard(
    session: Session,
    onCookieChange: (String) -> Unit,
    onRoomChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = AppTheme.colors.surfaceHigh,
        focusedBorderColor = AppTheme.colors.accent,
        cursorColor = AppTheme.colors.accent,
        unfocusedLabelColor = AppTheme.colors.textMuted,
    )

    AppCard(
        modifier = modifier,
        title = "Connection",
        trailing = { StatusChip(status = session.status) },
        collapsible = true,
        persistKey = "dashboard.connection",
    ) {
        // Token input
        OutlinedTextField(
            value = session.cookie,
            onValueChange = onCookieChange,
            label = { Text("mc_jwt token", fontSize = 12.sp) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = AppTheme.shapes.smallShape,
            colors = fieldColors,
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = session.room,
            onValueChange = onRoomChange,
            label = { Text("Room code (optional)", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = AppTheme.shapes.smallShape,
            colors = fieldColors,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Login / Logout
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onLogin,
                modifier = Modifier.weight(1f),
                enabled = !session.connected && !session.busy,
                shape = AppTheme.shapes.smallShape,
            ) {
                Text("Login with Discord", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onLogout,
                enabled = session.cookie.isNotBlank() && !session.connected,
                shape = AppTheme.shapes.smallShape,
            ) {
                Text("Logout", fontSize = 12.sp)
            }
        }

        // Error message
        if (session.error.isNotBlank() && session.status == SessionStatus.ERROR) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = session.error,
                color = AppTheme.colors.bad,
                fontSize = 11.sp,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Connect / Disconnect
        Button(
            onClick = { if (session.connected) onDisconnect() else onConnect() },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            enabled = !session.busy && session.cookie.isNotBlank(),
            shape = AppTheme.shapes.smallShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (session.connected) AppTheme.colors.bad else AppTheme.colors.good,
            ),
        ) {
            Text(
                text = when {
                    session.busy -> "Connecting..."
                    session.connected -> "Disconnect"
                    else -> "Connect"
                },
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}
