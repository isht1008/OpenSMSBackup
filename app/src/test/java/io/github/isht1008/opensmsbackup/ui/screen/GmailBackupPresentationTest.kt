package io.github.isht1008.opensmsbackup.ui.screen

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletion
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupCompletionState
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupScope
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupUiStage
import io.github.isht1008.opensmsbackup.gmail.work.GmailBackupUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailBackupPresentationTest {
    @Test fun activeAndTerminalStatesChooseExactlyOneGmailSurface() {
        val active = GmailBackupSurfaceDecision.resolve(true, true, false, false)
        assertTrue(active.showProgress)
        assertFalse(active.showResult)
        assertFalse(active.showGenericStatus)

        val terminal = GmailBackupSurfaceDecision.resolve(false, true, false, false)
        assertFalse(terminal.showProgress)
        assertTrue(terminal.showResult)
        assertFalse(terminal.showGenericStatus)
    }

    @Test fun localVerificationAndIdleRetainGenericStatus() {
        assertTrue(GmailBackupSurfaceDecision.resolve(false, true, true, false).showGenericStatus)
        assertTrue(GmailBackupSurfaceDecision.resolve(false, true, false, true).showGenericStatus)
        assertTrue(GmailBackupSurfaceDecision.resolve(false, false, false, false).showGenericStatus)
    }

    @Test fun cancelledAndFailedWorkWithoutOutputStillHaveOneTerminalResult() {
        val cancelled = GmailBackupUiState(
            stage = GmailBackupUiStage.CANCELLED,
            workId = java.util.UUID.randomUUID()
        )
        val failed = cancelled.copy(stage = GmailBackupUiStage.FAILED)
        assertTrue(cancelled.hasTerminalResult())
        assertTrue(failed.hasTerminalResult())
        assertFalse(GmailBackupUiState().hasTerminalResult())
    }

    @Test fun successfulUnchangedRunIsCompactMaskedAndHasNoEtaOrGenericUnchanged() {
        val presentation = completion().toPresentation()
        assertEquals("Gmail backup complete", presentation.title)
        assertEquals("Incremental · Archive", presentation.typeAndMode)
        assertEquals("Completed in 17s", presentation.durationLine)
        assertTrue(presentation.outcomeLines.contains("No new conversations to upload"))
        assertEquals("u***@example.com", presentation.details.toMap()["Account"])
        assertFalse(presentation.details.any { it.first == "Unchanged" })
        assertFalse((presentation.outcomeLines + presentation.details.map { it.second }).any { it.contains("Stopped") })
        assertFalse((presentation.outcomeLines + presentation.details.map { it.second }).any { it.contains("user@example.com") })
    }

    @Test fun canonicalScopeModeAndStateLabelsAreUserFacing() {
        assertEquals("Full reconciliation", GmailBackupScope.FULL.displayLabel())
        assertEquals("Recent-10 test", GmailBackupScope.RECENT_TEST.displayLabel())
        assertEquals("Archive", GmailBackupMode.ARCHIVE_APPEND_ONLY.displayLabel())
        assertEquals("Mirror", GmailBackupMode.MIRROR.displayLabel())
        assertEquals("Completed", GmailBackupCompletionState.COMPLETED.displayLabel())
        assertEquals("Completed with failures", GmailBackupCompletionState.COMPLETED_WITH_FAILURES.displayLabel())
    }

    @Test fun uploadCountUsesCorrectSingularAndPlural() {
        val one = completion(uploaded = 1, locallyUnchanged = 0).toPresentation()
        val two = completion(uploaded = 2, locallyUnchanged = 0).toPresentation()
        assertTrue(one.outcomeLines.contains("1 conversation uploaded"))
        assertTrue(two.outcomeLines.contains("2 conversations uploaded"))
    }

    @Test fun failedRunIsIncompleteAndUsesCanonicalDetails() {
        val presentation = completion(
            state = GmailBackupCompletionState.ABORTED_FATAL,
            uploaded = 2,
            failed = 1,
            remaining = 417
        ).toPresentation()
        assertEquals("Gmail backup incomplete", presentation.title)
        assertEquals("Stopped after 17s", presentation.durationLine)
        assertTrue(presentation.outcomeLines.contains("2 uploaded · 1 failed · 417 remaining"))
        assertEquals(
            listOf(
                "Backup type", "Mode", "Account", "Source conversations", "Source messages",
                "Checked", "Locally unchanged", "Legacy initialized locally", "Checked remotely",
                "Remote check - no update", "Uploaded", "Recoveries", "Failed", "Remaining",
                "Completion state", "Index recovery used", "Total duration"
            ),
            presentation.details.map { it.first }
        )
    }

    @Test fun recentTestWarnsThatItIsNotFull() {
        val presentation = completion(scope = GmailBackupScope.RECENT_TEST).toPresentation()
        assertEquals("Not a full backup", presentation.limitedNotice)
    }

    private fun completion(
        state: GmailBackupCompletionState = GmailBackupCompletionState.COMPLETED,
        scope: GmailBackupScope = GmailBackupScope.INCREMENTAL,
        uploaded: Int = 0,
        locallyUnchanged: Int = 3_330,
        failed: Int = 0,
        remaining: Int = 0
    ) = GmailBackupCompletion(
        state = state,
        checked = 3_330,
        total = 3_330,
        uploaded = uploaded,
        unchanged = locallyUnchanged,
        failed = failed,
        locallyUnchanged = locallyUnchanged,
        remaining = remaining,
        accountEmail = "user@example.com",
        sourceConversationTotal = 3_330,
        sourceMessageTotal = 34_950,
        durationMillis = 17_316L,
        backupScope = scope,
        backupMode = GmailBackupMode.ARCHIVE_APPEND_ONLY
    )
}
