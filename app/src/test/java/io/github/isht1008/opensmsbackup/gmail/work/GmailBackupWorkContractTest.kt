package io.github.isht1008.opensmsbackup.gmail.work

import androidx.work.Data
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletion
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletionState
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupScope
import io.github.isht1008.opensmsbackup.gmail.error.GmailErrorClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GmailBackupWorkContractTest {
    @Test fun `profile input round trips without sensitive payloads`() {
        val input = GmailBackupWorkInput(
            profileId = "profile-a",
            requestId = UUID.randomUUID().toString(),
            createdAt = 123L,
            executionMode = BackupExecutionMode.MANUAL,
            includeContactNames = true,
            backupScope = GmailBackupScope.FULL
        )
        val data = GmailBackupWorkContract.inputData(input)

        assertEquals(input, GmailBackupWorkContract.readInput(data))
        val keys = data.keyValueMap.keys.joinToString().lowercase()
        assertFalse(keys.contains("token"))
        assertFalse(keys.contains("sms"))
        assertFalse(keys.contains("mime"))
        assertFalse(keys.contains("body"))
    }

    @Test fun `missing input is rejected`() {
        assertNull(GmailBackupWorkContract.readInput(Data.EMPTY))
    }

    @Test fun `legacy progress without timing never becomes device uptime`() {
        val restored = requireNotNull(
            GmailBackupWorkContract.readProgress(
                Data.Builder()
                    .putString("gmail.phase", GmailBackupPhase.RUNNING.name)
                    .putString("gmail.profile_id", "profile-a")
                    .putString("gmail.status_message", "Running")
                    .build()
            )
        )
        assertEquals(0L, restored.elapsedMillis)
        assertEquals(0L, restored.startedAtEpochMillis)
        assertNull(restored.approximateEtaSeconds)
    }

    @Test fun `legacy input without scope decodes as recent test`() {
        val data = Data.Builder()
            .putString("gmail.profile_id", "profile-a")
            .putString("gmail.request_id", UUID.randomUUID().toString())
            .putLong("gmail.created_at", 123L)
            .putString("gmail.execution_mode", BackupExecutionMode.MANUAL.name)
            .putBoolean("gmail.include_contact_names", true)
            .build()

        val restored = requireNotNull(GmailBackupWorkContract.readInput(data))

        assertEquals(GmailBackupScope.RECENT_TEST, restored.backupScope)
    }

    @Test fun `invalid explicit scope fails safely`() {
        val data = Data.Builder()
            .putString("gmail.profile_id", "profile-a")
            .putString("gmail.request_id", UUID.randomUUID().toString())
            .putLong("gmail.created_at", 123L)
            .putString("gmail.execution_mode", BackupExecutionMode.MANUAL.name)
            .putBoolean("gmail.include_contact_names", true)
            .putString("gmail.backup_scope", "UNRECOGNIZED")
            .build()

        assertNull(GmailBackupWorkContract.readInput(data))
    }

    @Test fun `terminal states and authorization round trip`() {
        GmailBackupCompletionState.entries.forEach { state ->
            val completion = GmailBackupCompletion(
                state = state,
                checked = 2,
                total = 3,
                uploaded = 1,
                unchanged = 1,
                failed = 0,
                accountEmail = "user@example.com",
                failure = if (state == GmailBackupCompletionState.ABORTED_FATAL) {
                    GmailErrorClassifier().classifyHttp(401)
                } else null
            )
            val restored = requireNotNull(
                GmailBackupWorkContract.readCompletion(
                    GmailBackupWorkContract.outputData(completion)
                )
            )
            assertEquals(state, restored.state)
            assertEquals(2, restored.checked)
            assertEquals(
                state == GmailBackupCompletionState.ABORTED_FATAL,
                restored.failure?.reauthorizationRequired == true
            )
        }
    }

    @Test fun `progress phases and bounded message round trip`() {
        listOf(
            GmailBackupPhase.PREPARING,
            GmailBackupPhase.CHECKING_LOCAL,
            GmailBackupPhase.INDEXING,
            GmailBackupPhase.COMPARING,
            GmailBackupPhase.UPLOADING,
            GmailBackupPhase.RUNNING,
            GmailBackupPhase.RETRYING,
            GmailBackupPhase.CANCELLING
        ).forEach { phase ->
            val restored = requireNotNull(
                GmailBackupWorkContract.readProgress(
                    GmailBackupWorkContract.progressData(
                        GmailBackupWorkProgress(
                            phase = phase,
                            profileId = "profile-a",
                            checked = 1,
                            total = 2,
                            indexedMessages = 12,
                            acceptedIndexMessages = 7,
                            conversationsPerMinute = 20,
                            approximateEtaSeconds = 60,
                            elapsedMillis = 12_000L,
                            startedAtEpochMillis = 500L,
                            requestId = "request-1",
                            legacyLocallyInitialized = 1,
                            remotelyUnchanged = 4,
                            statusMessage = "x".repeat(2_000)
                        )
                    )
                )
            )
            assertEquals(phase, restored.phase)
            assertEquals(12, restored.indexedMessages)
            assertEquals(7, restored.acceptedIndexMessages)
            assertEquals(20, restored.conversationsPerMinute)
            assertEquals(60L, restored.approximateEtaSeconds)
            assertEquals(12_000L, restored.elapsedMillis)
            assertEquals(500L, restored.startedAtEpochMillis)
            assertEquals("request-1", restored.requestId)
            assertEquals(1, restored.legacyLocallyInitialized)
            assertEquals(4, restored.remotelyUnchanged)
            assertTrue(restored.statusMessage.length <= 500)
        }
    }

    @Test fun `limited test completion metadata survives process recreation`() {
        val completion = GmailBackupCompletion(
            GmailBackupCompletionState.LIMITED_TEST_COMPLETED,
            checked = 10, total = 10, uploaded = 8, unchanged = 2, failed = 0,
            previousSnapshotsTrashed = 3,
            totalMessages = 90, isLimitedTest = true,
            sourceConversationTotal = 4_534, sourceMessageTotal = 40_000
        )
        val restored = requireNotNull(
            GmailBackupWorkContract.readCompletion(GmailBackupWorkContract.outputData(completion))
        )
        assertTrue(restored.isLimitedTest)
        assertEquals(3, restored.previousSnapshotsTrashed)
        assertEquals(10, restored.total)
        assertEquals(4_534, restored.sourceConversationTotal)
        assertEquals(90, restored.totalMessages)
        assertEquals(40_000, restored.sourceMessageTotal)
    }

    @Test fun `detailed completion counters and timing survive process recreation`() {
        val completion = GmailBackupCompletion(
            state = GmailBackupCompletionState.COMPLETED_WITH_FAILURES,
            checked = 8,
            total = 10,
            uploaded = 2,
            unchanged = 5,
            failed = 1,
            locallyUnchanged = 4,
            legacyLocallyInitialized = 3,
            remotelyCompared = 4,
            remotelyUnchanged = 1,
            remoteRecoveries = 1,
            durationMillis = 90_000L,
            backupScope = GmailBackupScope.INCREMENTAL,
            gmailIndexUsed = true
        )
        val restored = requireNotNull(
            GmailBackupWorkContract.readCompletion(GmailBackupWorkContract.outputData(completion))
        )
        assertEquals(4, restored.locallyUnchanged)
        assertEquals(3, restored.legacyLocallyInitialized)
        assertEquals(4, restored.remotelyCompared)
        assertEquals(1, restored.remotelyUnchanged)
        assertEquals(2, restored.uploaded)
        assertEquals(1, restored.failed)
        assertEquals(2, restored.remaining)
        assertEquals(90_000L, restored.durationMillis)
        assertEquals(GmailBackupScope.INCREMENTAL, restored.backupScope)
        assertTrue(restored.gmailIndexUsed)
    }

    @Test fun `unique work names and tags are stable and profile scoped`() {
        assertEquals(
            GmailBackupWorkContract.uniqueWorkName("a"),
            GmailBackupWorkContract.uniqueWorkName("a")
        )
        assertNotEquals(
            GmailBackupWorkContract.uniqueWorkName("a"),
            GmailBackupWorkContract.uniqueWorkName("b")
        )
        assertNotEquals(
            GmailBackupWorkContract.profileTag("a"),
            GmailBackupWorkContract.profileTag("b")
        )
        assertEquals(
            123L,
            GmailBackupWorkContract.createdAt(
                setOf(GmailBackupWorkContract.createdTag(123L))
            )
        )
    }
}
