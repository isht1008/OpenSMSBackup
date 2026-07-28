package io.github.isht1008.opensmsbackup.gmail.work

import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.gmail.backup.GmailBackupScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GmailBackupWorkCoordinatorTest {
    @Test fun `same profile active work prevents duplicate enqueue`() = runBlocking {
        val activeId = UUID.randomUUID()
        val gateway = FakeGateway(profileActiveId = activeId)
        val result = enqueue(gateway)
        assertEquals(GmailBackupEnqueueResult.AlreadyRunning(activeId), result)
        assertNull(gateway.enqueuedRequest)
    }

    @Test fun `global active work conservatively prevents another profile`() = runBlocking {
        val activeId = UUID.randomUUID()
        val gateway = FakeGateway(globalActiveId = activeId)
        val result = enqueue(gateway, profileId = "profile-b")
        assertEquals(GmailBackupEnqueueResult.AlreadyRunning(activeId), result)
        assertNull(gateway.enqueuedRequest)
    }

    @Test fun `manual enqueue uses immutable profile mode name and tags`() = runBlocking {
        val gateway = FakeGateway()
        val result = enqueue(gateway, scope = GmailBackupScope.FULL)
        assertTrue(result is GmailBackupEnqueueResult.Enqueued)
        assertEquals(
            GmailBackupWorkContract.uniqueWorkName("profile-a"),
            gateway.enqueuedName
        )
        val request = requireNotNull(gateway.enqueuedRequest)
        val input = requireNotNull(GmailBackupWorkContract.readInput(request.workSpec.input))
        assertEquals("profile-a", input.profileId)
        assertEquals(BackupExecutionMode.MANUAL, input.executionMode)
        assertEquals(GmailBackupScope.FULL, input.backupScope)
        assertTrue(request.tags.contains(GmailBackupWorkContract.MANUAL_WORK_TAG))
        assertTrue(request.tags.contains(GmailBackupWorkContract.profileTag("profile-a")))
    }

    @Test fun `recent scope is immutable WorkManager input`() = runBlocking {
        val gateway = FakeGateway()

        enqueue(gateway, scope = GmailBackupScope.RECENT_TEST)

        val request = requireNotNull(gateway.enqueuedRequest)
        val input = requireNotNull(GmailBackupWorkContract.readInput(request.workSpec.input))
        assertEquals(GmailBackupScope.RECENT_TEST, input.backupScope)
        assertEquals(10, input.backupScope.conversationLimit)
    }

    @Test fun `incremental scope is immutable WorkManager input`() = runBlocking {
        val gateway = FakeGateway()
        enqueue(gateway, scope = GmailBackupScope.INCREMENTAL)
        val request = requireNotNull(gateway.enqueuedRequest)
        val input = requireNotNull(GmailBackupWorkContract.readInput(request.workSpec.input))
        assertEquals(GmailBackupScope.INCREMENTAL, input.backupScope)
        assertEquals("profile-a", input.profileId)
    }

    @Test fun `full mirror is blocked before enqueue`() = runBlocking {
        val gateway = FakeGateway()
        val result = enqueue(
            gateway,
            scope = GmailBackupScope.FULL,
            mode = GmailBackupMode.MIRROR
        )
        assertTrue(result is GmailBackupEnqueueResult.Blocked)
        assertNull(gateway.enqueuedRequest)
    }

    @Test fun `cancel targets exact work request`() {
        val gateway = FakeGateway()
        val workId = UUID.randomUUID()
        coordinator(gateway).cancel(workId)
        assertEquals(workId, gateway.cancelledId)
    }

    private fun coordinator(gateway: FakeGateway) =
        GmailBackupWorkCoordinator(gateway)

    private suspend fun enqueue(
        gateway: FakeGateway,
        profileId: String = "profile-a",
        scope: GmailBackupScope = GmailBackupScope.RECENT_TEST,
        mode: GmailBackupMode = GmailBackupMode.ARCHIVE_APPEND_ONLY
    ) = coordinator(gateway).enqueueManual(
        profileId = profileId,
        includeContactNames = true,
        backupScope = scope,
        backupMode = mode
    )

    private class FakeGateway(
        private val globalActiveId: UUID? = null,
        private val profileActiveId: UUID? = null
    ) : GmailBackupWorkGateway {
        var enqueuedName: String? = null
        var enqueuedRequest: OneTimeWorkRequest? = null
        var cancelledId: UUID? = null

        override suspend fun activeGlobalId() = globalActiveId
        override suspend fun activeProfileId(profileId: String) = profileActiveId
        override fun enqueueUnique(name: String, request: OneTimeWorkRequest) {
            enqueuedName = name
            enqueuedRequest = request
        }
        override fun observeAll(): Flow<List<WorkInfo>> = flowOf(emptyList())
        override fun cancel(workId: UUID) {
            cancelledId = workId
        }
    }
}
