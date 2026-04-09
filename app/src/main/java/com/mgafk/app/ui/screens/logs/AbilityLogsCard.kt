package com.mgafk.app.ui.screens.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AbilityLog
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AbilityLogsCard(
    logs: List<AbilityLog>,
    apiReady: Boolean = false,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE) }
    var query by rememberSaveable { mutableStateOf("") }

    val filteredLogs = remember(logs, query) {
        if (query.isBlank()) logs
        else {
            val q = query.trim().lowercase()
            logs.filter { log ->
                val abilityName = MgApi.abilityDisplayName(log.action).lowercase()
                abilityName.contains(q) ||
                    log.action.lowercase().contains(q) ||
                    log.petName.lowercase().contains(q) ||
                    log.petSpecies.lowercase().contains(q)
            }
        }
    }

    AppCard(
        modifier = modifier,
        title = "Ability Logs",
        collapsible = true,
        trailing = {
            if (logs.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${filteredLogs.size}/${logs.size}", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear",
                        fontSize = 11.sp,
                        color = Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onClear() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) {
        if (logs.isEmpty()) {
            Text("No abilities logged yet.", fontSize = 12.sp, color = TextMuted)
        } else {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search ability, pet name, species...", fontSize = 12.sp) },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Accent,
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredLogs.isEmpty()) {
                Text("No matching logs.", fontSize = 12.sp, color = TextMuted)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        LogRow(log, dateFormat, apiReady)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: AbilityLog, dateFormat: SimpleDateFormat, apiReady: Boolean) {
    val abilityName = remember(log.action, apiReady) { MgApi.abilityDisplayName(log.action) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceBorder.copy(alpha = 0.3f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (log.petSpecies.isNotBlank()) {
            SpriteImage(category = "pets", name = log.petSpecies, size = 24.dp, contentDescription = log.petSpecies)
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(abilityName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Accent)
            if (log.petName.isNotBlank()) {
                Text("${log.petName} · ${log.petSpecies}", fontSize = 11.sp, color = TextMuted)
            }
        }

        Text(
            text = dateFormat.format(Date(log.timestamp)),
            fontSize = 10.sp,
            color = TextMuted,
        )
    }
}
