package io.github.isht1008.opensmsbackup.gmail.work

import androidx.work.Data
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletion
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletionState
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
            includeContactNames = true
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
                            statusMessage = "x".repeat(2_000)
                        )
                    )
                )
            )
            assertEquals(phase, restored.phase)
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
