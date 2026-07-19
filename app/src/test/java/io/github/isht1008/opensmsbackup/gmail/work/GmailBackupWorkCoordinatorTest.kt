package io.github.isht1008.opensmsbackup.gmail.work

import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
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
        val result = coordinator(gateway).enqueueManual("profile-a", true, 3)
        assertEquals(GmailBackupEnqueueResult.AlreadyRunning(activeId), result)
        assertNull(gateway.enqueuedRequest)
    }

    @Test fun `global active work conservatively prevents another profile`() = runBlocking {
        val activeId = UUID.randomUUID()
        val gateway = FakeGateway(globalActiveId = activeId)
        val result = coordinator(gateway).enqueueManual("profile-b", true, 3)
        assertEquals(GmailBackupEnqueueResult.AlreadyRunning(activeId), result)
        assertNull(gateway.enqueuedRequest)
    }

    @Test fun `manual enqueue uses immutable profile mode name and tags`() = runBlocking {
        val gateway = FakeGateway()
        val result = coordinator(gateway).enqueueManual("profile-a", true, 3)
        assertTrue(result is GmailBackupEnqueueResult.Enqueued)
        assertEquals(
            GmailBackupWorkContract.uniqueWorkName("profile-a"),
            gateway.enqueuedName
        )
        val request = requireNotNull(gateway.enqueuedRequest)
        val input = requireNotNull(GmailBackupWorkContract.readInput(request.workSpec.input))
        assertEquals("profile-a", input.profileId)
        assertEquals(BackupExecutionMode.MANUAL, input.executionMode)
        assertTrue(request.tags.contains(GmailBackupWorkContract.MANUAL_WORK_TAG))
        assertTrue(request.tags.contains(GmailBackupWorkContract.profileTag("profile-a")))
    }

    @Test fun `normal production Gmail backup has no conversation limit`() = runBlocking {
        val gateway = FakeGateway()

        coordinator(gateway).enqueueManual("profile-a", true, null)

        val request = requireNotNull(gateway.enqueuedRequest)
        val input = requireNotNull(GmailBackupWorkContract.readInput(request.workSpec.input))
        assertNull(input.maximumConversations)
    }

    @Test fun `cancel targets exact work request`() {
        val gateway = FakeGateway()
        val workId = UUID.randomUUID()
        coordinator(gateway).cancel(workId)
        assertEquals(workId, gateway.cancelledId)
    }

    private fun coordinator(gateway: FakeGateway) =
        GmailBackupWorkCoordinator(gateway)

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
