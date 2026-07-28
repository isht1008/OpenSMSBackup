package io.github.isht1008.opensmsbackup.gmail.work

import java.util.UUID

enum class GmailBackupUiStage {
    IDLE,
    ENQUEUING,
    PREPARING,
    RUNNING,
    RETRYING,
    CANCELLING,
    COMPLETED,
    COMPLETED_WITH_FAILURES,
    CANCELLED,
    ABORTED,
    FAILED
}

data class GmailBackupUiState(
    val stage: GmailBackupUiStage = GmailBackupUiStage.IDLE,
    val workId: UUID? = null,
    val requestId: String? = null,
    val profileId: String? = null,
    val accountEmail: String? = null,
    val checked: Int = 0,
    val total: Int = 0,
    val uploaded: Int = 0,
    val unchanged: Int = 0,
    val failed: Int = 0,
    val locallyUnchanged: Int = 0,
    val legacyLocallyInitialized: Int = 0,
    val remotelyCompared: Int = 0,
    val remotelyUnchanged: Int = 0,
    val remoteRecoveries: Int = 0,
    val indexedMessages: Int = 0,
    val acceptedIndexMessages: Int = 0,
    val conversationsPerMinute: Int = 0,
    val approximateEtaSeconds: Long? = null,
    val elapsedMillis: Long = 0L,
    val startedAtEpochMillis: Long = 0L,
    val phase: String = "Ready",
    val retryAttempt: Int = 0,
    val retryMaximum: Int = 0
) {
    val remaining: Int
        get() = (total - checked).coerceAtLeast(0)

    val fraction: Float? = total.takeIf { it > 0 }?.let { checked.toFloat() / it }
    val isActive = stage in setOf(
        GmailBackupUiStage.ENQUEUING,
        GmailBackupUiStage.PREPARING,
        GmailBackupUiStage.RUNNING,
        GmailBackupUiStage.RETRYING,
        GmailBackupUiStage.CANCELLING
    )
    val isCancellable = isActive && stage != GmailBackupUiStage.CANCELLING
}
