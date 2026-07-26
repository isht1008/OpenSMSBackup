package io.github.isht1008.opensmsbackup.gmail.api

import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailConnectivityPreflightTest {
    private val profile = AccountProfileEntity(
        profileId = "profile-a",
        accountEmail = "account@example.com",
        connectionState = AccountProfileEntity.CONNECTION_STATE_CONNECTED
    )

    @Test
    fun `successful read-only profile check allows backup to continue`() = runBlocking {
        var checkedProfileId: String? = null
        val preflight = GmailConnectivityPreflight { checked ->
            checkedProfileId = checked.profileId
            Result.success(Unit)
        }

        assertTrue(preflight.check(profile).isSuccess)
        assertEquals("profile-a", checkedProfileId)
    }

    @Test
    fun `failed profile check is returned and can block enqueue`() = runBlocking {
        val failure = IllegalStateException("unavailable")
        val preflight = GmailConnectivityPreflight { Result.failure(failure) }

        val result = preflight.check(profile)

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}
