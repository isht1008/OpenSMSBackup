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
    @Test fun `incremental counters survive progress mapping`() {
        val workId = UUID.randomUUID()
        val mapped = GmailBackupWorkStateMapper.map(
            workId = workId,
            state = WorkInfo.State.RUNNING,
            progress = GmailBackupWorkProgress(
                phase = GmailBackupPhase.COMPARING,
                profileId = "profile-a",
                checked = 8,
                total = 10,
                uploaded = 1,
                unchanged = 6,
                failed = 1,
                locallyUnchanged = 5,
                legacyLocallyInitialized = 4,
                remotelyCompared = 3,
                remotelyUnchanged = 2,
                remoteRecoveries = 1,
                elapsedMillis = 20_000L,
                startedAtEpochMillis = 1_000L,
                approximateEtaSeconds = 30L,
                statusMessage = "Comparing changed conversations"
            ),
            completion = null
        )

        assertEquals(5, mapped.locallyUnchanged)
        assertEquals(4, mapped.legacyLocallyInitialized)
        assertEquals(3, mapped.remotelyCompared)
        assertEquals(2, mapped.remotelyUnchanged)
        assertEquals(1, mapped.remoteRecoveries)
        assertEquals(20_000L, mapped.elapsedMillis)
        assertEquals(30L, mapped.approximateEtaSeconds)
    }

    @Test fun `full completion with failures has distinct UI state and counts`() {
        val result = GmailBackupWorkStateMapper.map(
            workId = UUID.randomUUID(),
            state = WorkInfo.State.SUCCEEDED,
            progress = null,
            completion = GmailBackupCompletion(
                state = GmailBackupCompletionState.COMPLETED_WITH_FAILURES,
                checked = 9,
                total = 12,
                uploaded = 7,
                unchanged = 1,
                failed = 1,
                reason = "Completed with failures"
            )
        )

        assertEquals(GmailBackupUiStage.COMPLETED_WITH_FAILURES, result.stage)
        assertEquals("Completed with failures", result.phase)
        assertEquals(9, result.checked)
        assertEquals(7, result.uploaded)
        assertEquals(1, result.unchanged)
        assertEquals(1, result.failed)
        assertEquals(3, result.remaining)
    }

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
            ,
            elapsedMillis = 45_000L,
            approximateEtaSeconds = 90L
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
        assertEquals(45_000L, cancelled.elapsedMillis)
        assertEquals(null, cancelled.approximateEtaSeconds)
        assertFalse(cancelled.isCancellable)
    }

    @Test fun `new request never inherits counters elapsed or eta`() {
        val previous = GmailBackupUiState(
            stage = GmailBackupUiStage.RUNNING,
            workId = workId,
            requestId = "old-request",
            checked = 19,
            total = 3_330,
            uploaded = 5,
            elapsedMillis = 521_000_000L,
            approximateEtaSeconds = 6_900L
        )
        val fresh = GmailBackupWorkStateMapper.map(
            workId = workId,
            state = WorkInfo.State.RUNNING,
            progress = GmailBackupWorkProgress(
                phase = GmailBackupPhase.PREPARING,
                profileId = "profile-a",
                requestId = "new-request",
                statusMessage = "Preparing backup"
            ),
            completion = null,
            previous = previous
        )
        assertEquals(0, fresh.checked)
        assertEquals(0, fresh.uploaded)
        assertEquals(0L, fresh.elapsedMillis)
        assertEquals(null, fresh.approximateEtaSeconds)
    }

    private fun completion(state: GmailBackupCompletionState) =
        GmailBackupCompletion(state, 3, 3, 1, 2, 0)
}
