package io.github.isht1008.opensmsbackup.gmail.mirror

import androidx.work.WorkInfo
import io.github.isht1008.opensmsbackup.database.MirrorReconciliationRunEntity
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullMirrorForegroundAndReconciliationTest {
    @Test fun `rejected foreground start records failure and never enters execution`() = runBlocking {
        var failureRecorded = false
        var executionEntered = false
        val started = FullMirrorForegroundStartBoundary.enter(
            startForeground = { throw IllegalStateException("foreground rejected") },
            recordRejectedStart = { failureRecorded = true }
        )
        if (started) executionEntered = true
        assertFalse(started)
        assertTrue(failureRecorded)
        assertFalse(executionEntered)
    }

    @Test fun `foreground cancellation remains cancellation`() {
        var failureRecorded = false
        var cancelled = false
        try {
            runBlocking {
                FullMirrorForegroundStartBoundary.enter(
                    startForeground = { throw CancellationException("cancel") },
                    recordRejectedStart = { failureRecorded = true }
                )
            }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertFalse(failureRecorded)
    }

    @Test fun `failed work with entirely pending journal can terminalize`() {
        val evidence = evidence(states = listOf(WorkInfo.State.FAILED), itemCount = 3_332, mutations = 0)
        assertTrue(FullMirrorFailedPlanReconciler.Policy.canTerminalize(evidence))
    }

    @Test fun `non terminal or duplicate work cannot terminalize`() {
        assertFalse(FullMirrorFailedPlanReconciler.Policy.canTerminalize(evidence(listOf(WorkInfo.State.RUNNING), 3_332, 0)))
        assertFalse(FullMirrorFailedPlanReconciler.Policy.canTerminalize(evidence(listOf(WorkInfo.State.FAILED, WorkInfo.State.FAILED), 3_332, 0)))
    }

    @Test fun `any mutation or ambiguous journal state blocks automatic terminalization`() {
        assertFalse(FullMirrorFailedPlanReconciler.Policy.canTerminalize(evidence(listOf(WorkInfo.State.FAILED), 3_332, 1)))
        assertFalse(FullMirrorFailedPlanReconciler.Policy.canTerminalize(evidence(listOf(WorkInfo.State.FAILED), 0, 0)))
    }

    @Test fun `old confirmation cannot authorize a fresh immutable plan`() {
        val old = binding("old-run")
        val fresh = binding("fresh-run")
        assertNotEquals(old.runId, fresh.runId)
        assertNotEquals(FullMirrorWorkContract.runTag(old.runId), FullMirrorWorkContract.runTag(fresh.runId))
        assertFalse(FullMirrorConfirmationPolicy.evaluate(preview(fresh), null, 1).allowed)
    }

    private fun evidence(states: List<WorkInfo.State>, itemCount: Int, mutations: Int) =
        FullMirrorFailedPlanReconciler.Evidence(run(), states, itemCount, mutations)

    private fun run() = MirrorReconciliationRunEntity(
        runId = "run", profileId = "profile-b", accountIdentity = "mirror@example.com",
        deviceId = "device-b", deviceLabelId = "label-b", expectedPolicy = "MIRROR",
        createdAt = 1, expiresAt = 2, localDatasetFingerprint = "local", remoteIndexFingerprint = "remote",
        localScanComplete = true, localConversations = 3_332, ownedRemoteConversations = 13,
        unchangedCount = 0, uploadNewCount = 3_319, replaceChangedCount = 13,
        trashRemoteOnlyCount = 0, recoverCacheCount = 0, conflictCount = 0, foreignIgnoredCount = 0,
        failedCount = 0, estimatedReads = 13, estimatedUploads = 3_332, estimatedTrashMoves = 13,
        estimatedDurationMillis = 1, status = FullMirrorRunStatus.CONFIRMED.name
    )

    private fun binding(runId: String) = FullMirrorBinding(runId, "profile-b", "mirror@example.com", "device-b", "label-b", "MIRROR")
    private fun preview(binding: FullMirrorBinding): FullMirrorPreview {
        val item = FullMirrorPreviewItem("item", "key", 1, FullMirrorAction.REPLACE_CHANGED, "local", "remote", "message")
        return FullMirrorPreview(binding, 1, 2, "local", "remote", 1, 1, 1, 0, listOf(item), true, remoteCandidates = 1)
    }
}