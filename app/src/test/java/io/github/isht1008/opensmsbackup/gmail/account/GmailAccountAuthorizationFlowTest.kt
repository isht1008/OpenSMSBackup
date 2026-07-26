package io.github.isht1008.opensmsbackup.gmail.account

import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailAccountAuthorizationFlowTest {
    private val revoked = AccountProfileEntity(
        profileId = "profile",
        accountEmail = "user@example.com",
        connectionState = AccountProfileEntity.CONNECTION_STATE_AUTHORIZATION_REQUIRED
    )

    @Test fun `revoked profile invokes Gmail authorization before becoming connected`() = runBlocking {
        val operations = FakeOperations()
        val result = GmailAccountAuthorizationFlow(operations).authorize(revoked)
        assertTrue(result.isSuccess)
        assertEquals(listOf("required", "authorize", "connected"), operations.calls)
        assertEquals(AccountProfileEntity.CONNECTION_STATE_CONNECTED, result.getOrThrow().connectionState)
    }

    @Test fun `authorization failure keeps profile unauthorized`() = runBlocking {
        val operations = FakeOperations(failAuthorization = true)
        val result = GmailAccountAuthorizationFlow(operations).authorize(revoked)
        assertTrue(result.isFailure)
        assertEquals(listOf("required", "authorize", "required"), operations.calls)
        assertEquals(AccountProfileEntity.CONNECTION_STATE_AUTHORIZATION_REQUIRED, operations.state)
    }

    @Test fun `sign-in state alone is never sufficient for Gmail authorization`() = runBlocking {
        val operations = FakeOperations(failAuthorization = true)
        GmailAccountAuthorizationFlow(operations).authorize(
            revoked.copy(connectionState = AccountProfileEntity.CONNECTION_STATE_CONNECTED)
        )
        assertEquals("authorize", operations.calls[1])
        assertEquals(AccountProfileEntity.CONNECTION_STATE_AUTHORIZATION_REQUIRED, operations.state)
    }

    private class FakeOperations(
        private val failAuthorization: Boolean = false
    ) : GmailAccountAuthorizationOperations {
        val calls = mutableListOf<String>()
        var state = AccountProfileEntity.CONNECTION_STATE_AUTHORIZATION_REQUIRED

        override suspend fun requestGmailAuthorization(profile: AccountProfileEntity) {
            calls += "authorize"
            if (failAuthorization) error("authorization failed")
        }

        override suspend fun markConnected(profile: AccountProfileEntity) {
            calls += "connected"
            state = AccountProfileEntity.CONNECTION_STATE_CONNECTED
        }

        override suspend fun markAuthorizationRequired(profile: AccountProfileEntity) {
            calls += "required"
            state = AccountProfileEntity.CONNECTION_STATE_AUTHORIZATION_REQUIRED
        }
    }
}
