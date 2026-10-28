package com.mgafk.app.ui.screens.bugreport

import com.mgafk.app.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.repository.BugReportComment
import com.mgafk.app.data.repository.MyBugReport
import com.mgafk.app.data.repository.StoredBugReport
import com.mgafk.app.ui.components.AppCard

// ── My reports ──

@Composable
internal fun MyReportsCard(
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
                        color = AppTheme.colors.accent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "Refresh",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTheme.colors.accent,
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
                color = AppTheme.colors.textMuted,
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
        Text(error, fontSize = 11.sp, color = AppTheme.colors.bad, lineHeight = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (state.mineLoaded) {
        if (state.myReports.isEmpty()) {
            Text("None of your reports was found on the server.", fontSize = 11.sp, color = AppTheme.colors.textMuted)
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
        color = AppTheme.colors.surfaceHigh,
    )
}

@Composable
private fun StoredReportRow(report: StoredBugReport) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(report.ref.ifBlank { "BR-${report.id}" }, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.accent)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            report.title,
            fontSize = 13.sp,
            color = AppTheme.colors.textPrimary,
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
                .clip(AppTheme.shapes.smallShape)
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(report.ref, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.accent)
                    val date = shortDate(report.createdAt)
                    if (date.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(date, fontSize = 10.sp, color = AppTheme.colors.textMuted)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    report.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.colors.textPrimary,
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
            Text("Fixed in version $fixedIn", fontSize = 11.sp, color = AppTheme.colors.good)
        }

        val publicReply = report.publicReply
        if (publicReply != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppTheme.shapes.smallShape)
                    .background(AppTheme.colors.accent.copy(alpha = 0.08f))
                    .border(1.dp, AppTheme.colors.accent.copy(alpha = 0.25f), AppTheme.shapes.smallShape)
                    .padding(10.dp),
            ) {
                Text("Developer reply", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.accent)
                Spacer(modifier = Modifier.height(2.dp))
                Text(publicReply, fontSize = 12.sp, color = AppTheme.colors.textPrimary, lineHeight = 16.sp)
            }
        }

        if (report.status == "needs_info") {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "The developer needs more information: answer with a comment below.",
                fontSize = 11.sp,
                color = AppTheme.colors.warn,
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
                    color = AppTheme.colors.textSecondary,
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
                Text("This report is closed to new comments.", fontSize = 11.sp, color = AppTheme.colors.textMuted)
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
        placeholder = { Text("Add a comment or answer a question", fontSize = 12.sp, color = AppTheme.colors.textMuted) },
        enabled = !sending,
        minLines = 2,
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
        modifier = Modifier.fillMaxWidth(),
        shape = AppTheme.shapes.smallShape,
        colors = reportFieldColors(),
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            if (error != null) {
                Text(error, fontSize = 11.sp, color = AppTheme.colors.bad, lineHeight = 15.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onSend,
            enabled = !sending && value.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppTheme.colors.accent,
                disabledContainerColor = AppTheme.colors.accent.copy(alpha = 0.25f),
            ),
            shape = AppTheme.shapes.smallShape,
        ) {
            if (sending) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AppTheme.colors.onAccent, strokeWidth = 2.dp)
            } else {
                Text("Send", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.onAccent)
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
            .clip(AppTheme.shapes.smallShape)
            .background(if (fromPlayer) AppTheme.colors.surfaceHigh.copy(alpha = 0.25f) else AppTheme.colors.accent.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (fromPlayer) "You" else "Developer",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (fromPlayer) AppTheme.colors.textSecondary else AppTheme.colors.accent,
            )
            val date = shortDate(comment.createdAt)
            if (date.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(date, fontSize = 10.sp, color = AppTheme.colors.textMuted)
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(comment.message, fontSize = 12.sp, color = AppTheme.colors.textPrimary, lineHeight = 16.sp)
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

@Composable
@ReadOnlyComposable
private fun statusColor(status: String): Color = when (status) {
    "fixed" -> AppTheme.colors.good
    "needs_info" -> AppTheme.colors.warn
    "new", "triaged", "confirmed", "in_progress" -> AppTheme.colors.accent
    else -> AppTheme.colors.textMuted
}

/** "2026-10-23" out of an ISO 8601 timestamp. */
private fun shortDate(iso: String?): String = iso?.take(10).orEmpty()
