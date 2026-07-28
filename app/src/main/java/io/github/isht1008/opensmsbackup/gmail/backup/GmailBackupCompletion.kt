package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.gmail.error.GmailFailure

enum class GmailBackupCompletionState {
    COMPLETED,
    COMPLETED_WITH_FAILURES,
    LIMITED_TEST_COMPLETED,
    CANCELLED,
    ABORTED_FATAL,
    ABORTED_REPEATED_FAILURES,
    FAILED_BEFORE_START
}

data class GmailBackupCompletion(
    val state: GmailBackupCompletionState,
    val checked: Int,
    val total: Int,
    val uploaded: Int,
    val unchanged: Int,
    val failed: Int,
    val locallyUnchanged: Int = 0,
    val legacyLocallyInitialized: Int = 0,
    val remotelyCompared: Int = 0,
    val remotelyUnchanged: Int = 0,
    val remoteRecoveries: Int = 0,
    val previousSnapshotsTrashed: Int = 0,
    val remaining: Int = (total - checked).coerceAtLeast(0),
    val reason: String? = null,
    val profileId: String? = null,
    val accountEmail: String? = null,
    val failure: GmailFailure? = null,
    val totalMessages: Int = 0,
    val stoppedAtSafetyLimit: Boolean = false,
    val isLimitedTest: Boolean = false,
    val sourceConversationTotal: Int = total,
    val sourceMessageTotal: Int = totalMessages,
    val durationMillis: Long = 0L,
    val backupScope: GmailBackupScope = GmailBackupScope.RECENT_TEST,
    val backupMode: GmailBackupMode? = null,
    val gmailIndexUsed: Boolean = false,
    val failures: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)
