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
     * Idempotency key of the submission in progress: created on the first Send and kept
     * until the report and all its screenshots are through. A retry after a timeout or a
     * failed screenshot then gets back the report the server may already have created
     * (200 with attachmentsReceived) instead of a duplicate.
     */
    private var pendingFollowToken: String? = null
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
        val token = pendingFollowToken ?: BugReportPayload.newFollowToken().also { pendingFollowToken = it }
        val draft = current.draft
        val screenshots = current.screenshots
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
                    send(draft, screenshots, snapshot, token)
                } catch (e: Exception) {
                    AppLog.e(TAG, "Bug report failed", e)
                    SendOutcome.Failed("Something went wrong while sending the report. Try again.", null)
                }
            }
            when (outcome) {
                is SendOutcome.Done -> {
                    pendingFollowToken = null
                    _state.update {
                        it.copy(
                            sending = false,
                            progress = "",
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
                    )
                }
            }
        }
    }

    /** Runs on IO. The report first, then each screenshot the server does not have yet. */
    private fun send(
        draft: BugReportDraft,
        screenshots: List<PickedScreenshot>,
        snapshot: BugReportSnapshot,
        token: String,
    ): SendOutcome {
        val app = getApplication<Application>()
        val crashLog = try {
            CrashLog.read(app)
        } catch (_: Exception) {
            ""
        }
        val body = BugReportPayload.submitBody(
            draft = BugReportPayload.redactDraft(draft, snapshot.mask),
            appVersion = BuildConfig.VERSION_NAME,
            followToken = token,
            attachmentsExpected = screenshots.size,
            playerId = snapshot.playerId,
            playerName = snapshot.playerName,
            gameVersion = snapshot.gameVersion,
            context = BugReportContext.build(environment(), snapshot, crashLog),
        )

        val created: BugReportCreated = when (val result = BugReportApi.submit(body, token)) {
            is BugReportResult.Ok -> result.value
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

        // Stored before the screenshots: the report exists now, whatever happens to them.
        store.add(
            StoredBugReport(
                id = created.id,
                followToken = created.followToken,
                ref = created.ref,
                title = BugReportPayload.normalizeTitle(draft.title),
                createdAtMs = System.currentTimeMillis(),
            )
        )
        val stored = store.load()
        _state.update { it.copy(storedReports = stored) }

        // On a retry the server already holds the first ones (identical bytes are
        // recognised anyway, so a resend never takes a second slot).
        val remaining = screenshots.drop(created.attachmentsReceived)
        var refused = 0
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
                refused++
            }
        }

        val note = when {
            refused == 1 -> "1 screenshot was refused by the server and is not attached."
            refused > 1 -> "$refused screenshots were refused by the server and are not attached."
            !created.created -> "This report had already reached the server."
            else -> null
        }
        return SendOutcome.Done(created.ref, note)
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
