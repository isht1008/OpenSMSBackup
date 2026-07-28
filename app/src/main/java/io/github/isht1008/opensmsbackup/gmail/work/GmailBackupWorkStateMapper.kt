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
                GmailBackupPhase.CHECKING_LOCAL,
                GmailBackupPhase.CONNECTING,
                GmailBackupPhase.INDEXING -> GmailBackupUiStage.PREPARING
                GmailBackupPhase.COMPARING,
                GmailBackupPhase.UPLOADING,
                GmailBackupPhase.RUNNING -> GmailBackupUiStage.RUNNING
                else -> GmailBackupUiStage.RUNNING
            }
            WorkInfo.State.SUCCEEDED -> when (completion?.state) {
                GmailBackupCompletionState.COMPLETED,
                GmailBackupCompletionState.LIMITED_TEST_COMPLETED -> GmailBackupUiStage.COMPLETED
                GmailBackupCompletionState.COMPLETED_WITH_FAILURES ->
                    GmailBackupUiStage.COMPLETED_WITH_FAILURES
                GmailBackupCompletionState.CANCELLED -> GmailBackupUiStage.CANCELLED
                GmailBackupCompletionState.ABORTED_FATAL,
                GmailBackupCompletionState.ABORTED_REPEATED_FAILURES -> GmailBackupUiStage.ABORTED
                else -> GmailBackupUiStage.FAILED
            }
            WorkInfo.State.CANCELLED -> GmailBackupUiStage.CANCELLED
            WorkInfo.State.FAILED -> GmailBackupUiStage.FAILED
        }
        val requestId = progress?.requestId
        val sameWork = previous.workId == workId &&
            (requestId == null || previous.requestId == null || previous.requestId == requestId)
        return GmailBackupUiState(
            stage = stage,
            workId = workId,
            requestId = requestId ?: previous.requestId.takeIf { sameWork },
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
            locallyUnchanged = progress?.locallyUnchanged ?: completion?.locallyUnchanged
                ?: previous.locallyUnchanged.takeIf { sameWork } ?: 0,
            legacyLocallyInitialized =
                progress?.legacyLocallyInitialized ?: completion?.legacyLocallyInitialized
                    ?: previous.legacyLocallyInitialized.takeIf { sameWork } ?: 0,
            remotelyCompared = progress?.remotelyCompared ?: completion?.remotelyCompared
                ?: previous.remotelyCompared.takeIf { sameWork } ?: 0,
            remotelyUnchanged = progress?.remotelyUnchanged ?: completion?.remotelyUnchanged
                ?: previous.remotelyUnchanged.takeIf { sameWork } ?: 0,
            remoteRecoveries = progress?.remoteRecoveries ?: completion?.remoteRecoveries
                ?: previous.remoteRecoveries.takeIf { sameWork } ?: 0,
            indexedMessages = progress?.indexedMessages
                ?: previous.indexedMessages.takeIf { sameWork } ?: 0,
            acceptedIndexMessages = progress?.acceptedIndexMessages
                ?: previous.acceptedIndexMessages.takeIf { sameWork } ?: 0,
            conversationsPerMinute = progress?.conversationsPerMinute
                ?: previous.conversationsPerMinute.takeIf { sameWork } ?: 0,
            approximateEtaSeconds = if (state.isFinished) null else {
                progress?.approximateEtaSeconds
                    ?: previous.approximateEtaSeconds.takeIf { sameWork }
            },
            elapsedMillis = completion?.durationMillis ?: progress?.elapsedMillis
                ?: previous.elapsedMillis.takeIf { sameWork } ?: 0L,
            startedAtEpochMillis = progress?.startedAtEpochMillis
                ?: previous.startedAtEpochMillis.takeIf { sameWork } ?: 0L,
            phase = progress?.statusMessage ?: completion?.reason ?: when (stage) {
                GmailBackupUiStage.COMPLETED -> "Gmail backup completed"
                GmailBackupUiStage.COMPLETED_WITH_FAILURES -> "Completed with failures"
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
