package io.github.isht1008.opensmsbackup.gmail.work

import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailBackupWorkerPreflightTest {
    @Test fun `existing connected profile executes backup`() {
        assertNull(GmailBackupWorkerPreflight.failureFor(profile()))
    }

    @Test fun `missing profile fails safely`() {
        val failure = requireNotNull(GmailBackupWorkerPreflight.failureFor(null))
        assertTrue(failure.reason!!.contains("no longer exists"))
    }

    @Test fun `authorization required stops before execution`() {
        val failure = requireNotNull(
            GmailBackupWorkerPreflight.failureFor(
                profile(AccountProfileEntity.CONNECTION_STATE_AUTHORIZATION_REQUIRED)
            )
        )
        assertTrue(failure.failure?.reauthorizationRequired == true)
    }

    @Test fun `temporary connected state is not changed by preflight`() {
        val connected = profile()
        assertNull(GmailBackupWorkerPreflight.failureFor(connected))
        assertFalse(
            connected.connectionState ==
                AccountProfileEntity.CONNECTION_STATE_AUTHORIZATION_REQUIRED
        )
    }

    private fun profile(
        state: String = AccountProfileEntity.CONNECTION_STATE_CONNECTED
    ) = AccountProfileEntity(
        profileId = "profile-a",
        accountEmail = "user@example.com",
        connectionState = state
    )
}
