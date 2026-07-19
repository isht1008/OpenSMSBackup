package io.github.isht1008.opensmsbackup.verification

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import org.junit.Assert.*
import org.junit.Test

class BackupVerificationWorkContractTest {
    @Test fun `unique work is account and device scoped`() {
        assertNotEquals(BackupVerificationWorkContract.uniqueName("a", "d"),
            BackupVerificationWorkContract.uniqueName("b", "d"))
        assertNotEquals(BackupVerificationWorkContract.uniqueName("a", "d"),
            BackupVerificationWorkContract.uniqueName("a", "e"))
    }
    @Test fun `work data contains only small scalar identifiers and progress`() {
        val input = BackupVerificationWorkContract.input("profile", "device")
        assertEquals("profile", BackupVerificationWorkContract.profile(input))
        assertEquals("device", BackupVerificationWorkContract.device(input))
        val value = BackupVerificationProgress(VerificationStage.COMPARING, 10, 100)
        assertEquals(value.copy(message = null), BackupVerificationWorkContract.readProgress(
            BackupVerificationWorkContract.progress(value)))
        assertTrue(input.toByteArray().size < 1_024)
    }

    @Test fun `cancelled result is represented in persistent history without message content`() {
        val result = BackupVerificationResult("p", "user@example.com", "d", "Phone",
            GmailBackupMode.MIRROR, 1, 2, 10, 2, 5, 1, 4, 6, 1, 0, 0, 40.0,
            BackupVerificationStatus.CANCELLED, emptyList())
        val entity = result.toEntity()
        assertEquals("CANCELLED", entity.status)
        assertFalse(entity.toString().contains("SMS body"))
    }
}
