package io.github.isht1008.opensmsbackup.gmail.account

import io.github.isht1008.opensmsbackup.database.AccountProfileEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailAccountExitControllerTest {
    private val profileA = profile("a", "a@example.com")
    private val profileB = profile("b", "b@example.com")

    @Test fun `disconnect confirmation and cancellation are typed and safe`() {
        val controller = GmailAccountExitController(FakeOperations())
        controller.request(profileA, GmailAccountExitAction.DISCONNECT)
        assertTrue(controller.state.isOpen)
        assertEquals(GmailAccountExitAction.DISCONNECT, controller.state.action)
        controller.cancel()
        assertFalse(controller.state.isOpen)
    }

    @Test fun `revoke requires exact trimmed case-insensitive confirmation`() {
        val controller = GmailAccountExitController(FakeOperations())
        controller.request(profileA, GmailAccountExitAction.REVOKE)
        listOf("", "REVOKED", "please REVOKE", "REV OKE").forEach {
            controller.updateConfirmationText(it)
            assertFalse(controller.state.isRevokeConfirmationValid)
        }
        listOf("REVOKE", "revoke", "  ReVoKe  ").forEach {
            controller.updateConfirmationText(it)
            assertTrue(controller.state.isRevokeConfirmationValid)
        }
    }

    @Test fun `invalid revoke confirmation is rejected before operations`() = runBlocking {
        val operations = FakeOperations()
        val controller = GmailAccountExitController(operations)
        controller.request(profileA, GmailAccountExitAction.REVOKE)
        assertEquals(GmailAccountExitResult.InvalidConfirmation, controller.confirm())
        assertTrue(operations.calls.isEmpty())
    }

    @Test fun `successful revocation follows required ordering`() = runBlocking {
        val operations = FakeOperations()
        val controller = ready(operations, GmailAccountExitAction.REVOKE)
        assertEquals(GmailAccountExitResult.Success(GmailAccountExitAction.REVOKE), controller.confirm())
        assertEquals(
            listOf("backup:a", "verification:a", "revoke:a", "clear", "disconnect:a", "authorization-required:a"),
            operations.calls
        )
        assertFalse(controller.state.isOpen)
    }

    @Test fun `revocation failure keeps account connected and permits retry`() = runBlocking {
        val operations = FakeOperations(failRevoke = true)
        val controller = ready(operations, GmailAccountExitAction.REVOKE)
        assertEquals(GmailAccountExitResult.Failure(false), controller.confirm())
        assertFalse(operations.calls.contains("clear"))
        assertFalse(operations.calls.contains("disconnect:a"))
        assertFalse(controller.state.isProcessing)
        assertTrue(controller.state.isOpen)
    }

    @Test fun `cleanup failure after revocation marks authorization required`() = runBlocking {
        val operations = FakeOperations(failClear = true)
        val controller = ready(operations, GmailAccountExitAction.REVOKE)
        assertEquals(GmailAccountExitResult.Failure(true), controller.confirm())
        assertEquals(listOf("backup:a", "verification:a", "revoke:a", "clear", "authorization-required:a"), operations.calls)
        assertTrue(controller.state.errorMessage!!.contains("was revoked"))
    }

    @Test fun `logical disconnect clears credential session but never revokes`() = runBlocking {
        val operations = FakeOperations()
        val controller = ready(operations, GmailAccountExitAction.DISCONNECT)
        assertEquals(GmailAccountExitResult.Success(GmailAccountExitAction.DISCONNECT), controller.confirm())
        assertEquals(listOf("backup:a", "verification:a", "clear", "disconnect:a"), operations.calls)
    }

    @Test fun `active backup blocks both operations`() = runBlocking {
        GmailAccountExitAction.entries.forEach { action ->
            val operations = FakeOperations(activeBackup = true)
            assertEquals(GmailAccountExitResult.Blocked(ActiveWorkReason.BACKUP), ready(operations, action).confirm())
            assertEquals(listOf("backup:a"), operations.calls)
        }
    }

    @Test fun `active verification blocks both operations`() = runBlocking {
        GmailAccountExitAction.entries.forEach { action ->
            val operations = FakeOperations(activeVerification = true)
            assertEquals(GmailAccountExitResult.Blocked(ActiveWorkReason.VERIFICATION), ready(operations, action).confirm())
            assertEquals(listOf("backup:a", "verification:a"), operations.calls)
        }
    }

    @Test fun `duplicate confirmation is blocked while operation is suspended`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val operations = FakeOperations(revokeGate = gate)
        val controller = ready(operations, GmailAccountExitAction.REVOKE)
        val first = async(start = CoroutineStart.UNDISPATCHED) { controller.confirm() }
        assertTrue(controller.state.isProcessing)
        assertEquals(GmailAccountExitResult.DuplicateSubmission, controller.confirm())
        gate.complete(Unit)
        assertEquals(GmailAccountExitResult.Success(GmailAccountExitAction.REVOKE), first.await())
        assertEquals(1, operations.calls.count { it == "revoke:a" })
    }

    @Test fun `target account isolation and local history are preserved`() = runBlocking {
        val operations = FakeOperations().apply {
            policies.putAll(mapOf("a" to "ARCHIVE_APPEND_ONLY", "b" to "MIRROR"))
            history.putAll(mapOf("a" to 4, "b" to 7))
        }
        val controller = ready(operations, GmailAccountExitAction.DISCONNECT)
        controller.confirm()
        assertEquals(listOf("a"), operations.disconnectedProfiles)
        assertEquals("ARCHIVE_APPEND_ONLY", operations.policies["a"])
        assertEquals("MIRROR", operations.policies["b"])
        assertEquals(4, operations.history["a"])
        assertEquals(7, operations.history["b"])
        assertNull(operations.gmailMutation)
        assertFalse(operations.disconnectedProfiles.contains(profileB.profileId))
    }

    private fun ready(operations: FakeOperations, action: GmailAccountExitAction) =
        GmailAccountExitController(operations).also {
            it.request(profileA, action)
            if (action == GmailAccountExitAction.REVOKE) it.updateConfirmationText("REVOKE")
        }

    private fun profile(id: String, email: String) = AccountProfileEntity(id, accountEmail = email)

    private class FakeOperations(
        private val activeBackup: Boolean = false,
        private val activeVerification: Boolean = false,
        private val failRevoke: Boolean = false,
        private val failClear: Boolean = false,
        private val revokeGate: CompletableDeferred<Unit>? = null
    ) : GmailAccountExitOperations {
        val calls = mutableListOf<String>()
        val disconnectedProfiles = mutableListOf<String>()
        val policies = mutableMapOf<String, String>()
        val history = mutableMapOf<String, Int>()
        var gmailMutation: String? = null

        override suspend fun hasActiveBackup(profileId: String) = activeBackup.also { calls += "backup:$profileId" }
        override suspend fun hasActiveVerification(profileId: String) = activeVerification.also { calls += "verification:$profileId" }
        override suspend fun revokeGoogleAccess(profile: AccountProfileEntity) {
            calls += "revoke:${profile.profileId}"
            revokeGate?.await()
            if (failRevoke) error("revocation failed")
        }
        override suspend fun clearCredentialSession() {
            calls += "clear"
            if (failClear) error("clear failed")
        }
        override suspend fun disconnectLocally(profile: AccountProfileEntity) {
            calls += "disconnect:${profile.profileId}"
            disconnectedProfiles += profile.profileId
        }
        override suspend fun markAuthorizationRequired(profile: AccountProfileEntity) {
            calls += "authorization-required:${profile.profileId}"
        }
    }
}
