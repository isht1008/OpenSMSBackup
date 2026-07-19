package io.github.isht1008.opensmsbackup.gmail.work

import androidx.work.WorkInfo
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletion
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletionState
import java.util.UUID

object GmailBackupWorkStateMapper {
    fun map(
        workId: UUID,
        state: WorkInfo.State,
        progress: GmailBackupWorkProgress?,
        completion: GmailBackupCompletion?,
        previous: GmailBackupUiState = GmailBackupUiState()
    ): GmailBackupUiState {
        val stage = when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> GmailBackupUiStage.PREPARING
            WorkInfo.State.RUNNING -> when (progress?.phase) {
                GmailBackupPhase.RETRYING -> GmailBackupUiStage.RETRYING
                GmailBackupPhase.CANCELLING -> GmailBackupUiStage.CANCELLING
                GmailBackupPhase.PREPARING,
                GmailBackupPhase.READING,
                GmailBackupPhase.CONNECTING -> GmailBackupUiStage.PREPARING
                else -> GmailBackupUiStage.RUNNING
            }
            WorkInfo.State.SUCCEEDED -> when (completion?.state) {
                GmailBackupCompletionState.COMPLETED -> GmailBackupUiStage.COMPLETED
                GmailBackupCompletionState.CANCELLED -> GmailBackupUiStage.CANCELLED
                GmailBackupCompletionState.ABORTED_FATAL,
                GmailBackupCompletionState.ABORTED_REPEATED_FAILURES -> GmailBackupUiStage.ABORTED
                else -> GmailBackupUiStage.FAILED
            }
            WorkInfo.State.CANCELLED -> GmailBackupUiStage.CANCELLED
            WorkInfo.State.FAILED -> GmailBackupUiStage.FAILED
        }
        val sameWork = previous.workId == workId
        return GmailBackupUiState(
            stage = stage,
            workId = workId,
            profileId = progress?.profileId ?: completion?.profileId
                ?: previous.profileId.takeIf { sameWork },
            accountEmail = progress?.accountEmail ?: completion?.accountEmail
                ?: previous.accountEmail.takeIf { sameWork },
            checked = progress?.checked ?: completion?.checked
                ?: previous.checked.takeIf { sameWork } ?: 0,
            total = progress?.total ?: completion?.total
                ?: previous.total.takeIf { sameWork } ?: 0,
            uploaded = progress?.uploaded ?: completion?.uploaded
                ?: previous.uploaded.takeIf { sameWork } ?: 0,
            unchanged = progress?.unchanged ?: completion?.unchanged
                ?: previous.unchanged.takeIf { sameWork } ?: 0,
            failed = progress?.failed ?: completion?.failed
                ?: previous.failed.takeIf { sameWork } ?: 0,
            phase = progress?.statusMessage ?: completion?.reason ?: when (stage) {
                GmailBackupUiStage.COMPLETED -> "Gmail backup completed"
                GmailBackupUiStage.CANCELLED -> "Gmail backup cancelled"
                GmailBackupUiStage.ABORTED -> "Gmail backup stopped early"
                GmailBackupUiStage.FAILED -> "Gmail backup failed"
                else -> "Preparing Gmail backup"
            },
            retryAttempt = progress?.retryAttempt ?: 0,
            retryMaximum = progress?.retryMaximum ?: 0
        )
    }
}
