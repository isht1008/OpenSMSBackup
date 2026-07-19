package io.github.isht1008.opensmsbackup.gmail.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailBackupUiStateTest {
    @Test fun `running retry and preparing states are cancellable`() {
        listOf(
            GmailBackupUiStage.PREPARING,
            GmailBackupUiStage.RUNNING,
            GmailBackupUiStage.RETRYING
        ).forEach { stage ->
            assertTrue(GmailBackupUiState(stage = stage).isCancellable)
        }
    }

    @Test fun `cancelling and terminal states disable cancel`() {
        assertFalse(
            GmailBackupUiState(stage = GmailBackupUiStage.CANCELLING).isCancellable
        )
        assertFalse(
            GmailBackupUiState(stage = GmailBackupUiStage.COMPLETED).isCancellable
        )
    }
}
