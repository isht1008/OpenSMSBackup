package io.github.isht1008.opensmsbackup.gmail.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailBackupSessionTest {

    @Test
    fun duplicateBackupStartIsRejectedAndCleanupAllowsNextStart() {
        val firstProfile = "profile-one"
        val secondProfile = "profile-two"

        GmailBackupSession.end(firstProfile)
        GmailBackupSession.end(secondProfile)

        assertTrue(GmailBackupSession.begin(firstProfile))
        assertFalse(GmailBackupSession.begin(secondProfile))
        assertTrue(GmailBackupSession.isActive(firstProfile))

        GmailBackupSession.end(firstProfile)

        assertFalse(GmailBackupSession.isActive(firstProfile))
        assertTrue(GmailBackupSession.begin(secondProfile))

        GmailBackupSession.end(secondProfile)
    }
}
