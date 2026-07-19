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
    CANCELLED,
    ABORTED,
    FAILED
}

data class GmailBackupUiState(
    val stage: GmailBackupUiStage = GmailBackupUiStage.IDLE,
    val workId: UUID? = null,
    val profileId: String? = null,
    val accountEmail: String? = null,
    val checked: Int = 0,
    val total: Int = 0,
    val uploaded: Int = 0,
    val unchanged: Int = 0,
    val failed: Int = 0,
    val phase: String = "Ready",
    val retryAttempt: Int = 0,
    val retryMaximum: Int = 0
) {
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
