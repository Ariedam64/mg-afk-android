package com.mgafk.app.ui.screens.bugreport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mgafk.app.data.repository.BugCategory
import com.mgafk.app.data.repository.BugFrequency
import com.mgafk.app.data.repository.BugReportComment
import com.mgafk.app.data.repository.BugReportDraft
import com.mgafk.app.data.repository.BugReportPayload
import com.mgafk.app.data.repository.BugReportSnapshot
import com.mgafk.app.data.repository.MyBugReport
import com.mgafk.app.data.repository.StoredBugReport
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.BgDark
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusConnecting
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary

private val THUMB_SIZE = 72.dp

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
) {
    val draft = state.draft
    val editable = !state.sending
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
            color = TextMuted,
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
                    enabled = editable,
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
            enabled = editable,
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
            enabled = editable,
            isError = errorField == "description",
            minLines = 4,
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (detailsOpen) "Hide steps and results" else "+ Steps, expected and actual result (optional)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Accent,
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
                enabled = editable,
                isError = errorField == "steps",
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(4.dp))
            ReportTextField(
                value = draft.expected,
                onValueChange = { value -> onDraftChange { it.copy(expected = value) } },
                label = "Expected result",
                max = BugReportPayload.DETAIL_MAX,
                enabled = editable,
                isError = errorField == "expected",
                minLines = 2,
            )
            Spacer(modifier = Modifier.height(4.dp))
            ReportTextField(
                value = draft.actual,
                onValueChange = { value -> onDraftChange { it.copy(actual = value) } },
                label = "Actual result",
                max = BugReportPayload.DETAIL_MAX,
                enabled = editable,
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
                    enabled = editable,
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
            enabled = editable,
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
            enabled = editable,
            sessionConnected = sessionConnected,
            onCheckedChange = { checked -> onDraftChange { it.copy(includeAccountState = checked) } },
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Also sent automatically: device model, Android and app version, connection state, " +
                "and the app's recent logs (crash log, connection and service logs). " +
                "Login tokens are never sent.",
            fontSize = 10.sp,
            color = TextMuted,
            lineHeight = 13.sp,
        )

        // ── Result ──
        val error = state.error
        if (error != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(error, fontSize = 12.sp, color = StatusError, lineHeight = 16.sp)
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
                containerColor = Accent,
                disabledContainerColor = Accent.copy(alpha = 0.25f),
                disabledContentColor = Color.White.copy(alpha = 0.5f),
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (state.sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(state.progress.ifBlank { "Sending…" }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            } else {
                Text("Send report", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
}

/** A selectable chip, styled like the mode pickers of the Settings section. */
@Composable
private fun OptionChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent.copy(alpha = 0.12f) else SurfaceBorder.copy(alpha = 0.2f))
            .border(
                1.dp,
                if (selected) Accent.copy(alpha = 0.5f) else SurfaceBorder.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Accent else TextSecondary,
        )
    }
}

@Composable
private fun reportFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = SurfaceBorder,
    cursorColor = Accent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextMuted,
)

/** A form field with a character counter against the backend's limit. */
@Composable
private fun ReportTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    max: Int,
    enabled: Boolean,
    isError: Boolean,
    placeholder: String = "",
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    val tooLong = value.trim().length > max
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = {
            if (placeholder.isNotEmpty()) Text(placeholder, fontSize = 12.sp, color = TextMuted)
        },
        supportingText = {
            Text(
                text = "${value.trim().length} / $max",
                fontSize = 10.sp,
                color = if (tooLong) StatusError else TextMuted,
            )
        },
        isError = isError || tooLong,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = reportFieldColors(),
    )
}

@Composable
private fun ScreenshotThumb(shot: PickedScreenshot, removable: Boolean, onRemove: () -> Unit) {
    val image = remember(shot.id) { shot.encoded.thumbnail.asImageBitmap() }
    Box(modifier = Modifier.size(THUMB_SIZE)) {
        Image(
            bitmap = image,
            contentDescription = "Screenshot",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp)),
        )
        if (removable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(BgDark.copy(alpha = 0.75f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove screenshot",
                    tint = TextPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ProcessingThumb() {
    Box(
        modifier = Modifier
            .size(THUMB_SIZE)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceBorder.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun AddScreenshotTile(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(THUMB_SIZE)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceBorder.copy(alpha = 0.2f))
            .border(1.dp, SurfaceBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.AddPhotoAlternate,
                contentDescription = "Add a screenshot",
                tint = if (enabled) Accent else TextMuted,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text("Add", fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun AccountStateRow(
    checked: Boolean,
    enabled: Boolean,
    sessionConnected: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceBorder.copy(alpha = 0.2f))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(end = 12.dp, top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Accent, uncheckedColor = TextMuted),
        )
        Column(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
            Text("Attach my account state", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "A copy of what the app already syncs for your account: garden, inventory, stats, " +
                    "journal and recent activity log. Your login token is never part of it.",
                fontSize = 10.sp,
                color = TextMuted,
                lineHeight = 13.sp,
            )
            if (!sessionConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "No session is connected: the account can only be attached while one is.",
                    fontSize = 10.sp,
                    color = StatusConnecting,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SentBanner(ref: String, note: String?, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(StatusConnected.copy(alpha = 0.1f))
            .border(1.dp, StatusConnected.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Report sent: $ref", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = StatusConnected)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Thanks! Follow its status and answer questions under My reports.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp,
            )
            if (note != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(note, fontSize = 11.sp, color = StatusConnecting, lineHeight = 15.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "OK",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = StatusConnected,
            modifier = Modifier.clickable { onDismiss() },
        )
    }
}

// ── My reports ──

@Composable
private fun MyReportsCard(
    state: BugReportUiState,
    onRefresh: () -> Unit,
    onComment: (Long, String, () -> Unit) -> Unit,
) {
    AppCard(
        title = "My reports",
        trailing = {
            if (state.storedReports.isNotEmpty()) {
                if (state.mineLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Accent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "Refresh",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onRefresh() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) {
        if (state.storedReports.isEmpty()) {
            Text(
                "Reports you send from this device show up here, with their status and the developer's answers.",
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 15.sp,
            )
        } else {
            MyReportsList(state = state, onComment = onComment)
        }
    }
}

@Composable
private fun MyReportsList(
    state: BugReportUiState,
    onComment: (Long, String, () -> Unit) -> Unit,
) {
    val error = state.mineError
    if (error != null) {
        Text(error, fontSize = 11.sp, color = StatusError, lineHeight = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (state.mineLoaded) {
        if (state.myReports.isEmpty()) {
            Text("None of your reports was found on the server.", fontSize = 11.sp, color = TextMuted)
        }
        state.myReports.forEachIndexed { index, report ->
            if (index > 0) ReportDivider()
            ReportItem(
                report = report,
                sending = state.commentSendingId == report.id,
                commentError = state.commentErrors[report.id],
                onComment = { message, onSent -> onComment(report.id, message, onSent) },
            )
        }
    } else {
        // Status not loaded (yet): what the device knows on its own.
        state.storedReports.forEachIndexed { index, stored ->
            if (index > 0) ReportDivider()
            StoredReportRow(stored)
        }
    }
}

@Composable
private fun ReportDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        thickness = 1.dp,
        color = SurfaceBorder,
    )
}

@Composable
private fun StoredReportRow(report: StoredBugReport) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(report.ref.ifBlank { "BR-${report.id}" }, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            report.title,
            fontSize = 13.sp,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReportItem(
    report: MyBugReport,
    sending: Boolean,
    commentError: String?,
    onComment: (String, () -> Unit) -> Unit,
) {
    // A question from the developer opens the report straight away.
    var expanded by remember(report.id) { mutableStateOf(report.status == "needs_info") }
    var reply by remember(report.id) { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(report.ref, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                    val date = shortDate(report.createdAt)
                    if (date.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(date, fontSize = 10.sp, color = TextMuted)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    report.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = if (expanded) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusBadge(status = report.status, label = report.statusLabel)
        }

        val fixedIn = report.fixedInVersion
        if (!fixedIn.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Fixed in version $fixedIn", fontSize = 11.sp, color = StatusConnected)
        }

        val publicReply = report.publicReply
        if (publicReply != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Accent.copy(alpha = 0.08f))
                    .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                Text("Developer reply", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                Spacer(modifier = Modifier.height(2.dp))
                Text(publicReply, fontSize = 12.sp, color = TextPrimary, lineHeight = 16.sp)
            }
        }

        if (report.status == "needs_info") {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "The developer needs more information: answer with a comment below.",
                fontSize = 11.sp,
                color = StatusConnecting,
                lineHeight = 15.sp,
            )
        }

        if (!expanded) {
            if (report.comments.isNotEmpty() || report.canComment) {
                Spacer(modifier = Modifier.height(4.dp))
                val count = report.comments.size
                Text(
                    text = when (count) {
                        0 -> "Add a comment"
                        1 -> "1 comment"
                        else -> "$count comments"
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = true }
                        .padding(vertical = 2.dp),
                )
            }
        } else {
            report.comments.forEach { comment ->
                Spacer(modifier = Modifier.height(6.dp))
                CommentBubble(comment)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (report.canComment) {
                CommentInput(
                    value = reply,
                    onValueChange = { reply = it },
                    sending = sending,
                    error = commentError,
                    onSend = { onComment(reply) { reply = "" } },
                )
            } else {
                Text("This report is closed to new comments.", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun CommentInput(
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    error: String?,
    onSend: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Add a comment or answer a question", fontSize = 12.sp, color = TextMuted) },
        enabled = !sending,
        minLines = 2,
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = reportFieldColors(),
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            if (error != null) {
                Text(error, fontSize = 11.sp, color = StatusError, lineHeight = 15.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onSend,
            enabled = !sending && value.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                disabledContainerColor = Accent.copy(alpha = 0.25f),
            ),
            shape = RoundedCornerShape(10.dp),
        ) {
            if (sending) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Send", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun CommentBubble(comment: BugReportComment) {
    val fromPlayer = comment.author == "reporter"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (fromPlayer) SurfaceBorder.copy(alpha = 0.25f) else Accent.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (fromPlayer) "You" else "Developer",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (fromPlayer) TextSecondary else Accent,
            )
            val date = shortDate(comment.createdAt)
            if (date.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(date, fontSize = 10.sp, color = TextMuted)
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(comment.message, fontSize = 12.sp, color = TextPrimary, lineHeight = 16.sp)
    }
}

@Composable
private fun StatusBadge(status: String, label: String) {
    val color = statusColor(status)
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun statusColor(status: String): Color = when (status) {
    "fixed" -> StatusConnected
    "needs_info" -> StatusConnecting
    "new", "triaged", "confirmed", "in_progress" -> Accent
    else -> TextMuted
}

/** "2026-10-23" out of an ISO 8601 timestamp. */
private fun shortDate(iso: String?): String = iso?.take(10).orEmpty()
