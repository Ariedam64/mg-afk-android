package com.mgafk.app.ui.screens.bugreport

import com.mgafk.app.ui.theme.AppTheme
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mgafk.app.data.repository.BugCategory
import com.mgafk.app.data.repository.BugFrequency
import com.mgafk.app.data.repository.BugReportDraft
import com.mgafk.app.data.repository.BugReportPayload
import com.mgafk.app.data.repository.BugReportSnapshot
import com.mgafk.app.ui.components.AppCard

internal val THUMB_SIZE = 72.dp

/**
 * "Report a bug" section: the report form, then the reports sent from this device
 * with their status and the developer's answers.
 *
 * [snapshot] is only called when the player taps Send, so the session data and logs
 * are those of that moment. [visible] refreshes "My reports" each time the section opens.
 */
@Composable
fun BugReportCards(
    visible: Boolean,
    sessionConnected: Boolean,
    snapshot: () -> BugReportSnapshot,
) {
    val bugViewModel: BugReportViewModel = viewModel()
    val state by bugViewModel.state.collectAsState()

    LaunchedEffect(visible) {
        if (visible) bugViewModel.refreshMine()
    }

    ReportFormCard(
        state = state,
        sessionConnected = sessionConnected,
        onDraftChange = { transform -> bugViewModel.updateDraft(transform) },
        onAddScreenshot = { uri -> bugViewModel.addScreenshot(uri) },
        onRemoveScreenshot = { id -> bugViewModel.removeScreenshot(id) },
        onSend = { bugViewModel.submit(snapshot()) },
        onDismissSent = { bugViewModel.dismissSent() },
        onDiscardPending = { bugViewModel.discardPending() },
    )
    MyReportsCard(
        state = state,
        onRefresh = { bugViewModel.refreshMine() },
        onComment = { reportId, message, onSent -> bugViewModel.postComment(reportId, message, onSent) },
    )
}

// ── Report form ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportFormCard(
    state: BugReportUiState,
    sessionConnected: Boolean,
    onDraftChange: ((BugReportDraft) -> BugReportDraft) -> Unit,
    onAddScreenshot: (Uri) -> Unit,
    onRemoveScreenshot: (Long) -> Unit,
    onSend: () -> Unit,
    onDismissSent: () -> Unit,
    onDiscardPending: () -> Unit,
) {
    val draft = state.draft
    val editable = !state.sending
    // A report the server already holds keeps its text: only its screenshots are left to send.
    val textEditable = editable && state.pendingRef == null
    val errorField = state.fieldError?.field
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val detailsOpen = showDetails || errorField == "steps" || errorField == "expected" || errorField == "actual"

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAddScreenshot(uri)
    }

    AppCard(title = "Report a bug") {
        Text(
            "Something broken, confusing or missing? Tell us here. The answer shows up under My reports.",
            fontSize = 11.sp,
            color = AppTheme.colors.textMuted,
            lineHeight = 15.sp,
        )

        // ── Category ──
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel("Category")
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BugCategory.entries.forEach { category ->
                OptionChip(
                    label = category.label,
                    selected = draft.category == category,
                    enabled = textEditable,
                    onClick = { onDraftChange { it.copy(category = category) } },
                )
            }
        }

        // ── Text ──
        Spacer(modifier = Modifier.height(12.dp))
        ReportTextField(
            value = draft.title,
            onValueChange = { value -> onDraftChange { it.copy(title = value) } },
            label = "Title",
            placeholder = "Pet team hotkeys do nothing",
            max = BugReportPayload.TITLE_MAX,
            enabled = textEditable,
            isError = errorField == "title",
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(4.dp))
        ReportTextField(
            value = draft.description,
            onValueChange = { value -> onDraftChange { it.copy(description = value) } },
            label = "What happened?",
            placeholder = "What you did, what went wrong, since when.",
            max = BugReportPayload.DESCRIPTION_MAX,
            enabled = textEditable,
            isError = errorField == "description",
            minLines = 4,
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (detailsOpen) "Hide steps and results" else "+ Steps, expected and actual result (optional)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = AppTheme.colors.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { showDetails = !detailsOpen }
                .padding(vertical = 4.dp),
        )
        if (detailsOpen) {
            Spacer(modifier = Modifier.height(6.dp))
            ReportTextField(
                value = draft.steps,
                onValueChange = { value -> onDraftChange { it.copy(steps = value) } },
                label = "Steps to reproduce",
                placeholder = "1. Open Pets\n2. Tap a team",
                max = BugReportPayload.DETAIL_MAX,
                enabled = textEditable,
                isError = errorField == "steps",
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(4.dp))
            ReportTextField(
                value = draft.expected,
                onValueChange = { value -> onDraftChange { it.copy(expected = value) } },
                label = "Expected result",
                max = BugReportPayload.DETAIL_MAX,
                enabled = textEditable,
                isError = errorField == "expected",
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(4.dp))
            ReportTextField(
                value = draft.actual,
                onValueChange = { value -> onDraftChange { it.copy(actual = value) } },
                label = "Actual result",
                max = BugReportPayload.DETAIL_MAX,
                enabled = textEditable,
                isError = errorField == "actual",
                minLines = 2,
            )
        }

        // ── Frequency ──
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel("How often does it happen?")
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BugFrequency.entries.forEach { frequency ->
                OptionChip(
                    label = frequency.label,
                    selected = draft.frequency == frequency,
                    enabled = textEditable,
                    onClick = { onDraftChange { it.copy(frequency = frequency) } },
                )
            }
        }

        // ── Contact ──
        Spacer(modifier = Modifier.height(12.dp))
        ReportTextField(
            value = draft.contact,
            onValueChange = { value -> onDraftChange { it.copy(contact = value) } },
            label = "Contact (optional)",
            placeholder = "Discord name, if you want to be reached outside the app",
            max = BugReportPayload.CONTACT_MAX,
            enabled = textEditable,
            isError = errorField == "contact",
            singleLine = true,
        )

        // ── Screenshots ──
        Spacer(modifier = Modifier.height(8.dp))
        SectionLabel("Screenshots (up to ${BugReportPayload.MAX_ATTACHMENTS})")
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.screenshots.forEach { shot ->
                ScreenshotThumb(
                    shot = shot,
                    removable = editable,
                    onRemove = { onRemoveScreenshot(shot.id) },
                )
            }
            repeat(state.processingScreenshots) {
                ProcessingThumb()
            }
            if (state.screenshots.size + state.processingScreenshots < BugReportPayload.MAX_ATTACHMENTS) {
                AddScreenshotTile(
                    enabled = editable,
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }
        }

        // ── Account state ──
        Spacer(modifier = Modifier.height(12.dp))
        AccountStateRow(
            checked = draft.includeAccountState,
            enabled = textEditable,
            sessionConnected = sessionConnected,
            onCheckedChange = { checked -> onDraftChange { it.copy(includeAccountState = checked) } },
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Also sent automatically: device model, Android and app version, connection state, " +
                "and the app's recent logs (crash log, connection and service logs). " +
                "Login tokens are never sent.",
            fontSize = 10.sp,
            color = AppTheme.colors.textMuted,
            lineHeight = 13.sp,
        )

        // ── Result ──
        val error = state.error
        if (error != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(error, fontSize = 12.sp, color = AppTheme.colors.bad, lineHeight = 16.sp)
        }
        val pendingRef = state.pendingRef
        if (pendingRef != null && !state.sending) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Start a new report instead (leaves $pendingRef as it is)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AppTheme.colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onDiscardPending() }
                    .padding(vertical = 4.dp),
            )
        }
        val sentRef = state.sentRef
        if (sentRef != null) {
            Spacer(modifier = Modifier.height(10.dp))
            SentBanner(ref = sentRef, note = state.sentNote, onDismiss = onDismissSent)
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onSend,
            enabled = !state.sending && state.processingScreenshots == 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppTheme.colors.accent,
                disabledContainerColor = AppTheme.colors.accent.copy(alpha = 0.25f),
                disabledContentColor = Color.White.copy(alpha = 0.5f),
            ),
            shape = AppTheme.shapes.smallShape,
        ) {
            if (state.sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AppTheme.colors.onAccent,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(state.progress.ifBlank { "Sending…" }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            } else {
                Text(
                    if (state.pendingRef != null) "Send the missing screenshots" else "Send report",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
