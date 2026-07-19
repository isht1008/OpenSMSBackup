package io.github.isht1008.opensmsbackup.gmail.work

import androidx.work.WorkInfo
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletion
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GmailBackupWorkStateMapperTest {
    private val workId = UUID.randomUUID()

    @Test fun `active WorkInfo progress restores after recreation`() {
        val restored = GmailBackupWorkStateMapper.map(
            workId,
            WorkInfo.State.RUNNING,
            progress = GmailBackupWorkProgress(
                phase = GmailBackupPhase.RUNNING,
                profileId = "profile-a",
                accountEmail = "user@example.com",
                checked = 12,
                total = 20,
                uploaded = 2,
                unchanged = 10,
                statusMessage = "Checking conversation 12 of 20"
            ),
            completion = null
        )
        assertEquals(GmailBackupUiStage.RUNNING, restored.stage)
        assertEquals(12, restored.checked)
        assertTrue(restored.isCancellable)
    }

    @Test fun `retry and terminal states restore buttons`() {
        val retry = GmailBackupWorkStateMapper.map(
            workId,
            WorkInfo.State.RUNNING,
            GmailBackupWorkProgress(
                GmailBackupPhase.RETRYING,
                "profile-a",
                retryAttempt = 2,
                retryMaximum = 3,
                statusMessage = "Retrying"
            ),
            null
        )
        assertEquals(GmailBackupUiStage.RETRYING, retry.stage)
        assertTrue(retry.isCancellable)

        val completed = GmailBackupWorkStateMapper.map(
            workId,
            WorkInfo.State.SUCCEEDED,
            null,
            completion(GmailBackupCompletionState.COMPLETED)
        )
        assertEquals(GmailBackupUiStage.COMPLETED, completed.stage)
        assertFalse(completed.isCancellable)
    }

    @Test fun `cancelled work retains last observed counts`() {
        val previous = GmailBackupUiState(
            stage = GmailBackupUiStage.CANCELLING,
            workId = workId,
            checked = 7,
            total = 10,
            uploaded = 2
        )
        val cancelled = GmailBackupWorkStateMapper.map(
            workId,
            WorkInfo.State.CANCELLED,
            null,
            null,
            previous
        )
        assertEquals(GmailBackupUiStage.CANCELLED, cancelled.stage)
        assertEquals(7, cancelled.checked)
        assertFalse(cancelled.isCancellable)
    }

    private fun completion(state: GmailBackupCompletionState) =
        GmailBackupCompletion(state, 3, 3, 1, 2, 0)
}
