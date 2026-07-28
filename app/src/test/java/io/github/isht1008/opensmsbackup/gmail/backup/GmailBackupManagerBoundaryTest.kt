package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GmailBackupManagerBoundaryTest {
    private val manager = GmailBackupManager()

    @Test fun `successful full invokes metadata advancement once`() = runBlocking {
        assertEquals(1, advancementCount(GmailBackupScope.FULL, null, 0))
    }

    @Test fun `failed full does not invoke metadata advancement`() = runBlocking {
        assertEquals(0, advancementCount(GmailBackupScope.FULL, null, 1))
    }

    @Test fun `aborted full does not invoke metadata advancement`() = runBlocking {
        assertEquals(
            0,
            advancementCount(
                GmailBackupScope.FULL,
                GmailBackupCompletionState.ABORTED_FATAL,
                1
            )
        )
    }

    @Test fun `recent test does not invoke metadata advancement`() = runBlocking {
        assertEquals(0, advancementCount(GmailBackupScope.RECENT_TEST, null, 0))
    }

    @Test fun `full mirror returns before downstream Gmail setup and mutation`() {
        val plan = GmailBackupRunPlanner.create(
            allConversations = emptyList(),
            scope = GmailBackupScope.FULL,
            mode = GmailBackupMode.MIRROR
        )
        var deviceProfileCreated = false
        var labelsAccessed = false
        var strategyConstructed = false
        var strategyExecuted = false
        var gmailMutated = false

        val result = manager.withExecutableRun(
            plan = plan,
            onBlocked = { "blocked" },
            execute = {
                deviceProfileCreated = true
                labelsAccessed = true
                strategyConstructed = true
                strategyExecuted = true
                gmailMutated = true
                "executed"
            }
        )

        assertEquals("blocked", result)
        assertFalse(deviceProfileCreated)
        assertFalse(labelsAccessed)
        assertFalse(strategyConstructed)
        assertFalse(strategyExecuted)
        assertFalse(gmailMutated)
    }

    @Test fun `failed full receives completed with failures state`() {
        assertEquals(
            GmailBackupCompletionState.COMPLETED_WITH_FAILURES,
            GmailBackupRunPlanner.completionState(
                scope = GmailBackupScope.FULL,
                abortState = null,
                failed = 1
            )
        )
    }

    private suspend fun advancementCount(
        scope: GmailBackupScope,
        abortState: GmailBackupCompletionState?,
        failed: Int
    ): Int {
        var count = 0
        manager.advanceFullBackupMetadataIfEligible(
            scope = scope,
            abortState = abortState,
            failed = failed
        ) {
            count++
        }
        return count
    }
}
