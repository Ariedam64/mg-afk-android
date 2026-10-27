package com.mgafk.app.ui.screens.bugreport

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mgafk.app.BuildConfig
import com.mgafk.app.data.AppLog
import com.mgafk.app.data.CrashLog
import com.mgafk.app.data.repository.BugReportApi
import com.mgafk.app.data.repository.BugReportContext
import com.mgafk.app.data.repository.BugReportCreated
import com.mgafk.app.data.repository.BugReportDraft
import com.mgafk.app.data.repository.BugReportEnvironment
import com.mgafk.app.data.repository.BugReportFieldError
import com.mgafk.app.data.repository.BugReportPayload
import com.mgafk.app.data.repository.BugReportResult
import com.mgafk.app.data.repository.BugReportSnapshot
import com.mgafk.app.data.repository.BugReportStore
import com.mgafk.app.data.repository.MyBugReport
import com.mgafk.app.data.repository.ScreenshotEncoder
import com.mgafk.app.data.repository.StoredBugReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** A screenshot picked in the form, already encoded for upload. */
class PickedScreenshot(val id: Long, val encoded: ScreenshotEncoder.Encoded)

data class BugReportUiState(
    val draft: BugReportDraft = BugReportDraft(),
    val screenshots: List<PickedScreenshot> = emptyList(),
    /** Screenshots still being decoded and compressed. */
    val processingScreenshots: Int = 0,
    val sending: Boolean = false,
    val progress: String = "",
    val error: String? = null,
    val fieldError: BugReportFieldError? = null,
    /** Ref (BR-xx) of the report just sent, shown until dismissed or the next send. */
    val sentRef: String? = null,
    val sentNote: String? = null,
    /**
     * Ref of a report the server already holds while some of its screenshots are not through.
     * Its text is locked: Send only retries the missing screenshots, or the player starts over.
     */
    val pendingRef: String? = null,
    /** Reports sent from this device, newest first. */
    val storedReports: List<StoredBugReport> = emptyList(),
    /** Their status as the server last returned it. */
    val myReports: List<MyBugReport> = emptyList(),
    val mineLoading: Boolean = false,
    val mineLoaded: Boolean = false,
    val mineError: String? = null,
    val commentSendingId: Long? = null,
    val commentErrors: Map<Long, String> = emptyMap(),
)

/**
 * State and network calls of the "Report a bug" section. Kept apart from MainViewModel:
 * the only thing it needs from the sessions is the [BugReportSnapshot] handed to [submit].
 */
class BugReportViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "BugReportViewModel"
    }

    private sealed class SendOutcome {
        class Done(val ref: String, val note: String?) : SendOutcome()
        class Failed(val message: String, val fieldError: BugReportFieldError?) : SendOutcome()
    }

    private val store = BugReportStore(application)
    private val _state = MutableStateFlow(BugReportUiState())
    val state: StateFlow<BugReportUiState> = _state.asStateFlow()

    /**
     * A submission the server may already hold. Its follow token is the idempotency key: a
     * retry after a timeout sends the same content under the same token and gets back the
     * report the server may have created instead of a duplicate. The token stays tied to
     * that content: the server answers by token alone, so edited text sent under it would
     * be dropped silently.
     */
    private class Pending(
        val token: String,
        val draft: BugReportDraft,
        val shotIds: List<Long>,
    ) {
        /** Set once the server confirmed the report: from then on only screenshots are retried. */
        var created: BugReportCreated? = null
        /** Screenshots uploaded, or refused for good: never sent again. */
        val doneIds = mutableSetOf<Long>()
        var refused = 0
    }

    private var pending: Pending? = null
    private var nextScreenshotId = 1L
    private var mineJob: Job? = null

    init {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { store.load() }
            _state.update { it.copy(storedReports = stored) }
        }
    }

    fun updateDraft(transform: (BugReportDraft) -> BugReportDraft) {
        _state.update { it.copy(draft = transform(it.draft), fieldError = null, error = null) }
    }

    fun dismissSent() {
        _state.update { it.copy(sentRef = null, sentNote = null) }
    }

    /** Forgets a report whose screenshots did not all go through, and unlocks the form. */
    fun discardPending() {
        if (_state.value.sending) return
        pending = null
        _state.update { it.copy(pendingRef = null, error = null) }
    }

    // ---- Screenshots ----

    fun addScreenshot(uri: Uri) {
        val current = _state.value
        if (current.screenshots.size + current.processingScreenshots >= BugReportPayload.MAX_ATTACHMENTS) return
        val id = nextScreenshotId++
        _state.update { it.copy(processingScreenshots = it.processingScreenshots + 1, error = null) }
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.Default) {
                try {
                    ScreenshotEncoder.encode(getApplication<Application>(), uri)
                } catch (e: Exception) {
                    AppLog.w(TAG, "Screenshot could not be encoded: ${e.message}")
                    null
                } catch (e: OutOfMemoryError) {
                    AppLog.w(TAG, "Screenshot too large to decode")
                    null
                }
            }
            _state.update { s ->
                val next = s.copy(processingScreenshots = (s.processingScreenshots - 1).coerceAtLeast(0))
                when {
                    encoded == null -> next.copy(error = "This image could not be read or made small enough. Try another one.")
                    next.screenshots.size >= BugReportPayload.MAX_ATTACHMENTS -> next
                    else -> next.copy(screenshots = next.screenshots + PickedScreenshot(id, encoded))
                }
            }
        }
    }

    fun removeScreenshot(id: Long) {
        _state.update { s -> s.copy(screenshots = s.screenshots.filterNot { it.id == id }) }
    }

    // ---- Sending ----

    /** Validates, then sends the report and its screenshots. [snapshot] is taken by the caller at tap time. */
    fun submit(snapshot: BugReportSnapshot) {
        val current = _state.value
        if (current.sending || current.processingScreenshots > 0) return
        val invalid = BugReportPayload.validate(current.draft)
        if (invalid != null) {
            _state.update { it.copy(fieldError = invalid, error = invalid.message) }
            return
        }
        val draft = current.draft
        val screenshots = current.screenshots
        val shotIds = screenshots.map { it.id }
        val previous = pending
        val submission = when {
            previous == null -> Pending(BugReportPayload.newFollowToken(), draft, shotIds)
            // The report exists: its text is locked, only its screenshots are left.
            previous.created != null -> previous
            // Unchanged since an attempt the server may have taken: same token.
            previous.draft == draft && previous.shotIds == shotIds -> previous
            // Changed before the server confirmed anything: a new submission.
            else -> Pending(BugReportPayload.newFollowToken(), draft, shotIds)
        }
        pending = submission
        _state.update {
            it.copy(
                sending = true,
                progress = "Sending report…",
                error = null,
                fieldError = null,
                sentRef = null,
                sentNote = null,
            )
        }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    send(submission, screenshots, snapshot)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Bug report failed", e)
                    SendOutcome.Failed("Something went wrong while sending the report. Try again.", null)
                }
            }
            when (outcome) {
                is SendOutcome.Done -> {
                    pending = null
                    _state.update {
                        it.copy(
                            sending = false,
                            progress = "",
                            pendingRef = null,
                            // The account-state choice carries over to the next report.
                            draft = BugReportDraft(includeAccountState = it.draft.includeAccountState),
                            screenshots = emptyList(),
                            sentRef = outcome.ref,
                            sentNote = outcome.note,
                        )
                    }
                    refreshMine()
                }
                is SendOutcome.Failed -> _state.update {
                    it.copy(
                        sending = false,
                        progress = "",
                        error = outcome.message,
                        fieldError = outcome.fieldError,
                        pendingRef = submission.created?.ref,
                    )
                }
            }
        }
    }

    /** Runs on IO. The report first, unless the server already confirmed it, then each screenshot not through yet. */
    private fun send(
        submission: Pending,
        screenshots: List<PickedScreenshot>,
        snapshot: BugReportSnapshot,
    ): SendOutcome {
        val confirmed = submission.created
        val created: BugReportCreated = if (confirmed != null) confirmed else when (val result = submitReport(submission, snapshot)) {
            is BugReportResult.Ok -> result.value.also { submission.created = it }
            is BugReportResult.Failed -> {
                val error = result.error
                val field = error.field
                return SendOutcome.Failed(
                    message = error.message,
                    fieldError = if (error.code == "invalid" && field != null) {
                        BugReportFieldError(field, error.message)
                    } else {
                        null
                    },
                )
            }
        }

        if (confirmed == null) {
            // Stored before the screenshots: the report exists now, whatever happens to them.
            store.add(
                StoredBugReport(
                    id = created.id,
                    followToken = created.followToken,
                    ref = created.ref,
                    title = BugReportPayload.normalizeTitle(submission.draft.title),
                    createdAtMs = System.currentTimeMillis(),
                )
            )
            val stored = store.load()
            _state.update { it.copy(storedReports = stored) }
        }

        // Each screenshot by its own id: a retry sends only those not through yet, whatever
        // was added or removed since. The server recognises identical bytes, so one whose
        // upload timed out but did arrive never takes a second slot.
        val remaining = screenshots.filter { it.id !in submission.doneIds }
        remaining.forEachIndexed { index, shot ->
            _state.update { it.copy(progress = "Uploading screenshot ${index + 1} of ${remaining.size}…") }
            val upload = BugReportApi.uploadAttachment(
                created.id,
                BugReportPayload.attachmentBody(
                    followToken = created.followToken,
                    mime = shot.encoded.mime,
                    data = BugReportPayload.encodeBase64(shot.encoded.bytes),
                    width = shot.encoded.width,
                    height = shot.encoded.height,
                ),
            )
            if (upload is BugReportResult.Failed) {
                if (upload.error.retryable) {
                    return SendOutcome.Failed(
                        "Report ${created.ref} was sent, but screenshot ${index + 1} could not be uploaded: " +
                            "${upload.error.message} Tap Send again to upload the rest.",
                        null,
                    )
                }
                // Refused for good (window closed, invalid image...): sending it again won't help.
                AppLog.w(TAG, "Screenshot ${index + 1} of ${created.ref} refused: ${upload.error.code}")
                submission.refused++
            }
            submission.doneIds += shot.id
        }

        val note = when {
            submission.refused == 1 -> "1 screenshot was refused by the server and is not attached."
            submission.refused > 1 -> "${submission.refused} screenshots were refused by the server and are not attached."
            confirmed == null && !created.created -> "This report had already reached the server."
            else -> null
        }
        return SendOutcome.Done(created.ref, note)
    }

    /** The report itself, with the content and token of [submission]. */
    private fun submitReport(submission: Pending, snapshot: BugReportSnapshot): BugReportResult<BugReportCreated> {
        val app = getApplication<Application>()
        val crashLog = try {
            CrashLog.read(app)
        } catch (_: Exception) {
            ""
        }
        val body = BugReportPayload.submitBody(
            draft = BugReportPayload.redactDraft(submission.draft, snapshot.mask),
            appVersion = BuildConfig.VERSION_NAME,
            followToken = submission.token,
            attachmentsExpected = submission.shotIds.size,
            playerId = snapshot.playerId,
            playerName = snapshot.playerName,
            gameVersion = snapshot.gameVersion,
            context = BugReportContext.build(environment(), snapshot, crashLog),
        )
        return BugReportApi.submit(body, submission.token)
    }

    private fun environment(): BugReportEnvironment {
        val uptimeMs = SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
        return BugReportEnvironment(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            language = Locale.getDefault().toLanguageTag(),
            uptimeSec = if (uptimeMs >= 0) uptimeMs / 1000 else null,
        )
    }

    // ---- My reports ----

    /** Reloads the reports stored on this device and their status from the server. */
    fun refreshMine() {
        mineJob?.cancel()
        _state.update { it.copy(mineLoading = true, mineError = null) }
        mineJob = viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { store.load() }
            if (stored.isEmpty()) {
                _state.update {
                    it.copy(storedReports = stored, myReports = emptyList(), mineLoading = false, mineLoaded = true)
                }
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                BugReportApi.fetchMine(BugReportPayload.mineBody(stored))
            }
            _state.update { s ->
                when (result) {
                    is BugReportResult.Ok -> s.copy(
                        storedReports = stored,
                        myReports = result.value,
                        mineLoading = false,
                        mineLoaded = true,
                    )
                    is BugReportResult.Failed -> s.copy(
                        storedReports = stored,
                        mineLoading = false,
                        mineError = result.error.message,
                    )
                }
            }
        }
    }

    /** Adds a comment to one of the stored reports; [onSent] runs once the server took it. */
    fun postComment(reportId: Long, message: String, onSent: () -> Unit) {
        if (_state.value.commentSendingId != null) return
        val invalid = BugReportPayload.validateComment(message)
        if (invalid != null) {
            _state.update { it.copy(commentErrors = it.commentErrors + (reportId to invalid)) }
            return
        }
        val token = _state.value.storedReports.firstOrNull { it.id == reportId }?.followToken
        if (token == null) {
            _state.update {
                it.copy(commentErrors = it.commentErrors + (reportId to "This report was not sent from this device."))
            }
            return
        }
        _state.update { it.copy(commentSendingId = reportId, commentErrors = it.commentErrors - reportId) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                BugReportApi.postComment(reportId, BugReportPayload.commentBody(token, message))
            }
            when (result) {
                is BugReportResult.Ok -> {
                    _state.update { it.copy(commentSendingId = null) }
                    onSent()
                    refreshMine()
                }
                is BugReportResult.Failed -> _state.update {
                    it.copy(
                        commentSendingId = null,
                        commentErrors = it.commentErrors + (reportId to result.error.message),
                    )
                }
            }
        }
    }
}
