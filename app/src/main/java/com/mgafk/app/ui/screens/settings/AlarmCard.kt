package com.mgafk.app.ui.screens.settings

import com.mgafk.app.ui.theme.AppTheme
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AlarmSchedule
import com.mgafk.app.data.model.AppSettings
import com.mgafk.app.data.model.isSilentAt
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import com.mgafk.app.ui.components.AppCard

// ── Alarm ──

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")
private val DAY_VALUES = listOf(1, 2, 3, 4, 5, 6, 7) // ISO 1=Mon..7=Sun

@Composable
internal fun AlarmCard(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
    onPreviewAlarm: () -> Unit,
    onStopPreviewAlarm: () -> Unit,
) {
    val context = LocalContext.current
    val schedules = settings.alarmSchedules

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        // null = "Silent" picked; we treat that the same as "default" for safety
        // (an alarm without sound defeats the purpose).
        onUpdate(settings.copy(alarmSoundUri = uri?.toString().orEmpty()))
    }

    val currentName = remember(settings.alarmSoundUri) {
        if (settings.alarmSoundUri.isBlank()) "System default"
        else runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(settings.alarmSoundUri))
                ?.getTitle(context)
        }.getOrNull() ?: "Custom"
    }

    // Live "Silenced now" indicator - recompute every 30s.
    var nowTick by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = LocalDateTime.now()
            delay(30_000L)
        }
    }
    val silencedNow = schedules.isSilentAt(nowTick)

    fun replaceSchedule(updated: AlarmSchedule) {
        onUpdate(settings.copy(alarmSchedules = schedules.map { if (it.id == updated.id) updated else it }))
    }

    AppCard(title = "Alarm", collapsible = true, persistKey = "settings_alarm") {
        // ── Sound subsection ──
        Text("Sound", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary)

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            "Sound played when an alert fires in Alarm mode.",
            fontSize = 11.sp,
            color = AppTheme.colors.textMuted,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppTheme.shapes.smallShape)
                .background(AppTheme.colors.surfaceHigh.copy(alpha = 0.2f))
                .clickable {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_TYPE,
                            RingtoneManager.TYPE_ALARM
                                or RingtoneManager.TYPE_NOTIFICATION
                                or RingtoneManager.TYPE_RINGTONE,
                        )
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Pick alarm sound")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        )
                        if (settings.alarmSoundUri.isNotBlank()) {
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                Uri.parse(settings.alarmSoundUri),
                            )
                        }
                    }
                    launcher.launch(intent)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tap to change", fontSize = 12.sp, color = AppTheme.colors.textMuted)
            Spacer(modifier = Modifier.weight(1f))
            Text(currentName, fontSize = 12.sp, color = AppTheme.colors.textSecondary)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Volume slider + Test button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Volume", fontSize = 12.sp, color = AppTheme.colors.textPrimary)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${(settings.alarmVolume * 100).toInt()}%",
                fontSize = 11.sp,
                color = AppTheme.colors.textMuted,
            )
        }

        Slider(
            value = settings.alarmVolume,
            onValueChange = { onUpdate(settings.copy(alarmVolume = it.coerceIn(0f, 1f))) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = AppTheme.colors.accent,
                activeTrackColor = AppTheme.colors.accent,
                inactiveTrackColor = AppTheme.colors.surfaceHigh.copy(alpha = 0.5f),
            ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppTheme.shapes.smallShape)
                .background(AppTheme.colors.accent.copy(alpha = 0.12f))
                .border(1.dp, AppTheme.colors.accent.copy(alpha = 0.5f), AppTheme.shapes.smallShape)
                .clickable { onPreviewAlarm() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Test sound", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.accent)
        }

        // Stop preview when this composable leaves composition (e.g., user navigates away).
        DisposableEffect(Unit) {
            onDispose { onStopPreviewAlarm() }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Schedule subsection ──
        Text("Schedule", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textPrimary)

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            "Mute alarms during these hours. Notifications still arrive silently. " +
                "Add a window per routine - work nights and weekend lie-ins need different hours.",
            fontSize = 11.sp,
            color = AppTheme.colors.textMuted,
            lineHeight = 15.sp,
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (schedules.isEmpty()) {
            Text(
                "No mute window yet. Alarms always ring.",
                fontSize = 11.sp,
                color = AppTheme.colors.textMuted,
            )
        }

        schedules.forEachIndexed { index, schedule ->
            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
            AlarmScheduleBlock(
                schedule = schedule,
                fallbackLabel = "Schedule ${index + 1}",
                mutedNow = schedule.isSilentAt(nowTick),
                context = context,
                onChange = { updated -> replaceSchedule(updated) },
                onDelete = {
                    onUpdate(settings.copy(alarmSchedules = schedules.filter { it.id != schedule.id }))
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppTheme.shapes.smallShape)
                .background(AppTheme.colors.accent.copy(alpha = 0.12f))
                .border(1.dp, AppTheme.colors.accent.copy(alpha = 0.4f), AppTheme.shapes.smallShape)
                .clickable {
                    val next = AlarmSchedule(
                        label = "Schedule ${schedules.size + 1}",
                        enabled = true,
                    )
                    onUpdate(settings.copy(alarmSchedules = schedules + next))
                }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("+ Add window", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.accent)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (silencedNow) "Silenced now: yes" else "Silenced now: no",
            fontSize = 11.sp,
            color = if (silencedNow) AppTheme.colors.accent else AppTheme.colors.textMuted,
        )
    }
}

/** One mute window: name, on/off, hours, active days. */
@Composable
private fun AlarmScheduleBlock(
    schedule: AlarmSchedule,
    fallbackLabel: String,
    mutedNow: Boolean,
    context: android.content.Context,
    onChange: (AlarmSchedule) -> Unit,
    onDelete: () -> Unit,
) {
    // Greyed-out controls when the window is off
    val controlsAlpha = if (schedule.enabled) 1f else 0.4f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.smallShape)
            .border(1.dp, AppTheme.colors.surfaceHigh.copy(alpha = 0.4f), AppTheme.shapes.smallShape)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = schedule.label,
                onValueChange = { onChange(schedule.copy(label = it)) },
                placeholder = { Text(fallbackLabel, fontSize = 12.sp, color = AppTheme.colors.textMuted) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppTheme.colors.accent,
                    unfocusedBorderColor = AppTheme.colors.surfaceHigh,
                    cursorColor = AppTheme.colors.accent,
                    focusedTextColor = AppTheme.colors.textPrimary,
                    unfocusedTextColor = AppTheme.colors.textPrimary,
                ),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = schedule.enabled,
                onCheckedChange = { onChange(schedule.copy(enabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = AppTheme.colors.accent),
            )
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Delete window",
                tint = AppTheme.colors.textMuted,
                modifier = Modifier
                    .size(32.dp)
                    .clip(AppTheme.shapes.smallShape)
                    .clickable { onDelete() }
                    .padding(6.dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimePickerBox(
                label = "From",
                minutes = schedule.startMinute,
                enabled = schedule.enabled,
                alpha = controlsAlpha,
                modifier = Modifier.weight(1f),
                onPick = { picked -> onChange(schedule.copy(startMinute = picked)) },
                context = context,
            )
            TimePickerBox(
                label = "To",
                minutes = schedule.endMinute,
                enabled = schedule.enabled,
                alpha = controlsAlpha,
                modifier = Modifier.weight(1f),
                suffix = if (schedule.startMinute > schedule.endMinute) " (overnight)" else null,
                onPick = { picked -> onChange(schedule.copy(endMinute = picked)) },
                context = context,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text("Active days", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppTheme.colors.textPrimary)

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DAY_VALUES.forEachIndexed { index, dayValue ->
                val isSelected = dayValue in schedule.activeDays
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 32.dp)
                        .clip(AppTheme.shapes.smallShape)
                        .background(
                            if (isSelected) AppTheme.colors.accent.copy(alpha = 0.12f * controlsAlpha)
                            else AppTheme.colors.surfaceHigh.copy(alpha = 0.2f * controlsAlpha)
                        )
                        .border(
                            1.dp,
                            if (isSelected) AppTheme.colors.accent.copy(alpha = 0.5f * controlsAlpha)
                            else AppTheme.colors.surfaceHigh.copy(alpha = 0.4f * controlsAlpha),
                            AppTheme.shapes.smallShape,
                        )
                        .clickable(enabled = schedule.enabled) {
                            val newDays = if (isSelected) schedule.activeDays - dayValue
                            else schedule.activeDays + dayValue
                            onChange(schedule.copy(activeDays = newDays))
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = DAY_LABELS[index],
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) AppTheme.colors.accent.copy(alpha = controlsAlpha)
                                else AppTheme.colors.textSecondary.copy(alpha = controlsAlpha),
                    )
                }
            }
        }

        if (mutedNow) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Muting now", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AppTheme.colors.accent)
        }
    }
}

@Composable
private fun TimePickerBox(
    label: String,
    minutes: Int,
    enabled: Boolean,
    alpha: Float,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    onPick: (Int) -> Unit,
    context: android.content.Context,
) {
    val hour = minutes / 60
    val minute = minutes % 60
    val timeText = "%02d:%02d".format(hour, minute)

    Row(
        modifier = modifier
            .clip(AppTheme.shapes.smallShape)
            .background(AppTheme.colors.surfaceHigh.copy(alpha = 0.2f * alpha))
            .clickable(enabled = enabled) {
                TimePickerDialog(
                    context,
                    { _, h, m -> onPick(h * 60 + m) },
                    hour, minute, true,
                ).show()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = AppTheme.colors.textMuted.copy(alpha = alpha))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            timeText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.colors.textPrimary.copy(alpha = alpha),
        )
        if (suffix != null) {
            Text(suffix, fontSize = 10.sp, color = AppTheme.colors.textMuted.copy(alpha = alpha))
        }
    }
}
