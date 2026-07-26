package io.github.isht1008.opensmsbackup.gmail.work

import androidx.work.Data
import androidx.work.WorkInfo
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletion
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletionState
import io.github.isht1008.opensmsbackup.gmail.error.GmailErrorClassifier
import java.util.UUID

enum class GmailBackupPhase {
    ENQUEUING,
    PREPARING,
    READING,
    CONNECTING,
    RUNNING,
    RETRYING,
    CANCELLING,
    FINALIZING
}

enum class BackupExecutionMode {
    MANUAL,
    SCHEDULED
}

data class GmailBackupWorkInput(
    val profileId: String,
    val requestId: String,
    val createdAt: Long,
    val executionMode: BackupExecutionMode,
    val includeContactNames: Boolean
)

data class GmailBackupWorkProgress(
    val phase: GmailBackupPhase,
    val profileId: String,
    val accountEmail: String? = null,
    val checked: Int = 0,
    val total: Int = 0,
    val uploaded: Int = 0,
    val unchanged: Int = 0,
    val failed: Int = 0,
    val retryAttempt: Int = 0,
    val retryMaximum: Int = 0,
    val statusMessage: String,
    val cancellationRequested: Boolean = false
) {
    val remaining: Int = (total - checked).coerceAtLeast(0)
    val fraction: Float? = total.takeIf { it > 0 }?.let { checked.toFloat() / it }
}

object GmailBackupWorkContract {
    const val ALL_WORK_TAG = "gmail-backup"
    const val MANUAL_WORK_TAG = "gmail-backup-manual"
    private const val PROFILE_TAG_PREFIX = "gmail-backup-profile-tag-"
    private const val CREATED_TAG_PREFIX = "gmail-backup-created-"
    private const val UNIQUE_WORK_PREFIX = "gmail-backup-profile-"

    private const val PROFILE_ID = "gmail.profile_id"
    private const val REQUEST_ID = "gmail.request_id"
    private const val CREATED_AT = "gmail.created_at"
    private const val EXECUTION_MODE = "gmail.execution_mode"
    private const val INCLUDE_CONTACT_NAMES = "gmail.include_contact_names"
    private const val PHASE = "gmail.phase"
    private const val ACCOUNT_EMAIL = "gmail.account_email"
    private const val CHECKED = "gmail.checked"
    private const val TOTAL = "gmail.total"
    private const val UPLOADED = "gmail.uploaded"
    private const val UNCHANGED = "gmail.unchanged"
    private const val FAILED = "gmail.failed"
    private const val PREVIOUS_SNAPSHOTS_TRASHED = "gmail.previous_snapshots_trashed"
    private const val RETRY_ATTEMPT = "gmail.retry_attempt"
    private const val RETRY_MAXIMUM = "gmail.retry_maximum"
    private const val STATUS_MESSAGE = "gmail.status_message"
    private const val CANCELLATION_REQUESTED = "gmail.cancellation_requested"
    private const val TERMINAL_STATE = "gmail.terminal_state"
    private const val REASON = "gmail.reason"
    private const val AUTHORIZATION_REQUIRED = "gmail.authorization_required"
    private const val SAFETY_LIMIT = "gmail.safety_limit"
    private const val LIMITED_TEST = "gmail.limited_test"
    private const val SOURCE_CONVERSATION_TOTAL = "gmail.source_conversation_total"
    private const val TOTAL_MESSAGES = "gmail.total_messages"
    private const val SOURCE_MESSAGE_TOTAL = "gmail.source_message_total"

    fun uniqueWorkName(profileId: String) = UNIQUE_WORK_PREFIX + profileId
    fun profileTag(profileId: String) = PROFILE_TAG_PREFIX + profileId
    fun createdTag(createdAt: Long) = CREATED_TAG_PREFIX + createdAt
    fun createdAt(tags: Set<String>): Long =
        tags.firstNotNullOfOrNull { tag ->
            tag.removePrefix(CREATED_TAG_PREFIX)
                .takeIf { tag.startsWith(CREATED_TAG_PREFIX) }
                ?.toLongOrNull()
        } ?: 0L

    fun inputData(input: GmailBackupWorkInput): Data =
        Data.Builder()
            .putString(PROFILE_ID, input.profileId)
            .putString(REQUEST_ID, input.requestId)
            .putLong(CREATED_AT, input.createdAt)
            .putString(EXECUTION_MODE, input.executionMode.name)
            .putBoolean(INCLUDE_CONTACT_NAMES, input.includeContactNames)
            .build()

    fun readInput(data: Data): GmailBackupWorkInput? {
        val profileId = data.getString(PROFILE_ID)?.takeIf { it.isNotBlank() } ?: return null
        val requestId = data.getString(REQUEST_ID)?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
            ?: return null
        val createdAt = data.getLong(CREATED_AT, -1L).takeIf { it > 0L } ?: return null
        val executionMode = data.getString(EXECUTION_MODE)
            ?.let { runCatching { BackupExecutionMode.valueOf(it) }.getOrNull() }
            ?: return null
        return GmailBackupWorkInput(
            profileId = profileId,
            requestId = requestId,
            createdAt = createdAt,
            executionMode = executionMode,
            includeContactNames = data.getBoolean(INCLUDE_CONTACT_NAMES, false)
        )
    }

    fun progressData(progress: GmailBackupWorkProgress): Data =
        Data.Builder()
            .putString(PHASE, progress.phase.name)
            .putString(PROFILE_ID, progress.profileId)
            .putString(ACCOUNT_EMAIL, progress.accountEmail)
            .putInt(CHECKED, progress.checked)
            .putInt(TOTAL, progress.total)
            .putInt(UPLOADED, progress.uploaded)
            .putInt(UNCHANGED, progress.unchanged)
            .putInt(FAILED, progress.failed)
            .putInt(RETRY_ATTEMPT, progress.retryAttempt)
            .putInt(RETRY_MAXIMUM, progress.retryMaximum)
            .putString(STATUS_MESSAGE, progress.statusMessage.take(500))
            .putBoolean(CANCELLATION_REQUESTED, progress.cancellationRequested)
            .build()

    fun readProgress(data: Data): GmailBackupWorkProgress? {
        val phase = data.getString(PHASE)?.let { runCatching { GmailBackupPhase.valueOf(it) }.getOrNull() }
            ?: return null
        val profileId = data.getString(PROFILE_ID) ?: return null
        return GmailBackupWorkProgress(
            phase = phase,
            profileId = profileId,
            accountEmail = data.getString(ACCOUNT_EMAIL),
            checked = data.getInt(CHECKED, 0),
            total = data.getInt(TOTAL, 0),
            uploaded = data.getInt(UPLOADED, 0),
            unchanged = data.getInt(UNCHANGED, 0),
            failed = data.getInt(FAILED, 0),
            retryAttempt = data.getInt(RETRY_ATTEMPT, 0),
            retryMaximum = data.getInt(RETRY_MAXIMUM, 0),
            statusMessage = data.getString(STATUS_MESSAGE).orEmpty(),
            cancellationRequested = data.getBoolean(CANCELLATION_REQUESTED, false)
        )
    }

    fun outputData(completion: GmailBackupCompletion): Data =
        Data.Builder()
            .putString(TERMINAL_STATE, completion.state.name)
            .putString(ACCOUNT_EMAIL, completion.accountEmail)
            .putString(PROFILE_ID, completion.profileId)
            .putInt(CHECKED, completion.checked)
            .putInt(TOTAL, completion.total)
            .putInt(UPLOADED, completion.uploaded)
            .putInt(UNCHANGED, completion.unchanged)
            .putInt(FAILED, completion.failed)
            .putInt(PREVIOUS_SNAPSHOTS_TRASHED, completion.previousSnapshotsTrashed)
            .putString(REASON, completion.reason?.take(500))
            .putBoolean(AUTHORIZATION_REQUIRED, completion.failure?.reauthorizationRequired == true)
            .putBoolean(SAFETY_LIMIT, completion.stoppedAtSafetyLimit)
            .putBoolean(LIMITED_TEST, completion.isLimitedTest)
            .putInt(SOURCE_CONVERSATION_TOTAL, completion.sourceConversationTotal)
            .putInt(TOTAL_MESSAGES, completion.totalMessages)
            .putInt(SOURCE_MESSAGE_TOTAL, completion.sourceMessageTotal)
            .build()

    fun readCompletion(data: Data): GmailBackupCompletion? {
        val state = data.getString(TERMINAL_STATE)
            ?.let { runCatching { GmailBackupCompletionState.valueOf(it) }.getOrNull() }
            ?: return null
        val authorizationRequired = data.getBoolean(AUTHORIZATION_REQUIRED, false)
        return GmailBackupCompletion(
            state = state,
            checked = data.getInt(CHECKED, 0),
            total = data.getInt(TOTAL, 0),
            uploaded = data.getInt(UPLOADED, 0),
            unchanged = data.getInt(UNCHANGED, 0),
            failed = data.getInt(FAILED, 0),
            previousSnapshotsTrashed = data.getInt(PREVIOUS_SNAPSHOTS_TRASHED, 0),
            reason = data.getString(REASON),
            profileId = data.getString(PROFILE_ID),
            accountEmail = data.getString(ACCOUNT_EMAIL),
            failure = if (authorizationRequired) {
                GmailErrorClassifier().classifyHttp(401)
            } else {
                null
            },
            stoppedAtSafetyLimit = data.getBoolean(SAFETY_LIMIT, false),
            isLimitedTest = data.getBoolean(LIMITED_TEST, false),
            sourceConversationTotal = data.getInt(SOURCE_CONVERSATION_TOTAL, data.getInt(TOTAL, 0)),
            totalMessages = data.getInt(TOTAL_MESSAGES, 0),
            sourceMessageTotal = data.getInt(SOURCE_MESSAGE_TOTAL, data.getInt(TOTAL_MESSAGES, 0))
        )
    }

    fun isActive(state: WorkInfo.State) =
        state == WorkInfo.State.ENQUEUED ||
            state == WorkInfo.State.RUNNING ||
            state == WorkInfo.State.BLOCKED
}
