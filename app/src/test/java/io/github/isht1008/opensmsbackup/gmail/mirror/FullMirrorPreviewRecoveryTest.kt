package io.github.isht1008.opensmsbackup.gmail.mirror

import androidx.work.WorkInfo
import io.github.isht1008.opensmsbackup.database.MirrorPreviewScanEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FullMirrorPreviewRecoveryTest {
    private val now = 10_000L

    @Test
    fun `missing old work continues the same durable scan`() {
        val scan = scan(metadataChecked = 701, fullReadsCompleted = 3)
        assertEquals(
            FullMirrorPreviewRecoveryDecision.ENQUEUE_CONTINUATION,
            decide(scan)
        )
        assertEquals("scan-safe", scan.scanId)
        assertEquals(701, scan.metadataChecked)
        assertEquals(3, scan.fullReadsCompleted)
    }

    @Test
    fun `cancelled old WorkSpec continues when Room was not explicitly cancelled`() {
        assertEquals(
            FullMirrorPreviewRecoveryDecision.ENQUEUE_CONTINUATION,
            decide(scan(), listOf(work(WorkInfo.State.CANCELLED)))
        )
    }

    @Test
    fun `failed old WorkSpec continues only while Room remains resumable`() {
        assertEquals(
            FullMirrorPreviewRecoveryDecision.ENQUEUE_CONTINUATION,
            decide(scan(), listOf(work(WorkInfo.State.FAILED)))
        )
        assertEquals(
            FullMirrorPreviewRecoveryDecision.IGNORE_TERMINAL,
            decide(scan(state = FullMirrorPreviewScanState.FAILED), listOf(work(WorkInfo.State.FAILED)))
        )
    }

    @Test
    fun `active same WorkSpec is observed without duplicate enqueue`() {
        listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED).forEach {
            assertEquals(
                FullMirrorPreviewRecoveryDecision.OBSERVE_ACTIVE,
                decide(scan(), listOf(work(it)))
            )
        }
    }

    @Test
    fun `active different scan fails closed as ambiguous`() {
        assertEquals(
            FullMirrorPreviewRecoveryDecision.BLOCK_AMBIGUOUS,
            decide(scan(), listOf(work(WorkInfo.State.RUNNING, "other-scan")))
        )
    }

    @Test
    fun `competing backup waits rather than enqueueing`() {
        assertEquals(
            FullMirrorPreviewRecoveryDecision.WAIT_FOR_CONFLICT,
            decide(scan(), competing = true)
        )
    }

    @Test
    fun `explicitly cancelled scan is never resurrected`() {
        val cancelled = scan(state = FullMirrorPreviewScanState.CANCELLED)
        assertEquals(FullMirrorPreviewRecoveryDecision.IGNORE_TERMINAL, decide(cancelled))
        assertTrue(FullMirrorPreviewInterruptionPolicy.isExplicitCancellation(cancelled))
        assertFalse(FullMirrorPreviewInterruptionPolicy.shouldCheckpoint(cancelled))
    }

    @Test
    fun `worker cancellation checkpoints running scan without calling it explicit`() {
        val running = scan(state = FullMirrorPreviewScanState.RUNNING)
        assertFalse(FullMirrorPreviewInterruptionPolicy.isExplicitCancellation(running))
        assertTrue(FullMirrorPreviewInterruptionPolicy.shouldCheckpoint(running))
    }

    @Test
    fun `expired scan is not resumed`() {
        assertEquals(
            FullMirrorPreviewRecoveryDecision.EXPIRE,
            decide(scan(expiresAt = now - 1))
        )
    }

    @Test
    fun `binding mismatch including Archive profile cannot resume`() {
        assertEquals(
            FullMirrorPreviewRecoveryDecision.FAIL_BINDING,
            decide(scan(), bindingValid = false)
        )
    }

    @Test
    fun `published scan is returned not republished`() {
        val published = scan(state = FullMirrorPreviewScanState.PUBLISHED)
            .copy(publishedRunId = "different-executable-run")
        assertEquals(FullMirrorPreviewRecoveryDecision.IGNORE_TERMINAL, decide(published))
        assertFalse(published.scanId == published.publishedRunId)
    }

    @Test
    fun `paused network scan remains resumable`() {
        assertEquals(
            FullMirrorPreviewRecoveryDecision.ENQUEUE_CONTINUATION,
            decide(scan(state = FullMirrorPreviewScanState.WAITING_NETWORK))
        )
    }

    @Test
    fun `restoring and resuming stages have dedicated user text`() {
        assertEquals(
            "Restoring Full Mirror preview",
            FullMirrorPreviewProgressText.title(FullMirrorPreviewStage.RESTORING)
        )
        assertEquals(
            "Resuming Full Mirror preview",
            FullMirrorPreviewProgressText.title(FullMirrorPreviewStage.RESUMING)
        )
        assertFalse(
            FullMirrorPreviewProgressText.title(FullMirrorPreviewStage.RESUMING)
                .contains("Creating local backup")
        )
    }

    @Test
    fun `work and scan diagnostics use non-reversible digests`() {
        val raw = "private-profile-or-device-id"
        val digest = FullMirrorPreviewDiagnostics.digest(raw)
        assertEquals(12, digest.length)
        assertFalse(digest.contains(raw))
        assertTrue(digest.matches(Regex("[0-9a-f]{12}")))
    }

    @Test
    fun `repeated startup reconciliation enqueues exactly one continuation`() = runBlocking {
        val gateway = RecordingRecoveryGateway()
        val engine = FullMirrorPreviewRecoveryEngine(gateway)
        val scan = scan(metadataChecked = 1_234, fullReadsCompleted = 7)

        val first = engine.reconcile(scan, true, false, now)
        val second = engine.reconcile(scan, true, false, now)

        assertEquals(FullMirrorPreviewRecoveryDecision.ENQUEUE_CONTINUATION, first.decision)
        assertEquals(FullMirrorPreviewRecoveryDecision.OBSERVE_ACTIVE, second.decision)
        assertEquals(1, gateway.enqueueCount)
        assertEquals(scan.scanId, gateway.enqueuedScan?.scanId)
        assertEquals(1_234, gateway.enqueuedScan?.metadataChecked)
        assertEquals(7, gateway.enqueuedScan?.fullReadsCompleted)
        assertEquals(first.workId, second.workId)
    }

    @Test
    fun `terminal old generation is replaced with the original scan input`() = runBlocking {
        val oldId = UUID.randomUUID()
        val gateway = RecordingRecoveryGateway(
            mutableListOf(
                PreviewWorkSnapshot(oldId, WorkInfo.State.CANCELLED, "scan-safe", 1, 4)
            )
        )
        val outcome = FullMirrorPreviewRecoveryEngine(gateway)
            .reconcile(scan(), true, false, now)

        assertEquals(FullMirrorPreviewRecoveryDecision.ENQUEUE_CONTINUATION, outcome.decision)
        assertEquals("CANCELLED", outcome.oldState)
        assertEquals(1, gateway.enqueueCount)
        assertFalse(oldId == outcome.workId)
        assertEquals("scan-safe", gateway.enqueuedScan?.scanId)
    }

    private fun decide(
        scan: MirrorPreviewScanEntity,
        old: List<PreviewWorkSnapshot> = emptyList(),
        bindingValid: Boolean = true,
        competing: Boolean = false
    ) = FullMirrorPreviewRecoveryPolicy.decide(scan, old, bindingValid, competing, now)

    private fun work(
        state: WorkInfo.State,
        scanId: String = "scan-safe"
    ) = PreviewWorkSnapshot(UUID.randomUUID(), state, scanId, 0, 0)

    private fun scan(
        state: FullMirrorPreviewScanState = FullMirrorPreviewScanState.RUNNING,
        expiresAt: Long = now + 50_000L,
        metadataChecked: Int = 0,
        fullReadsCompleted: Int = 0
    ) = MirrorPreviewScanEntity(
        scanId = "scan-safe",
        profileId = "mirror-profile",
        accountFingerprint = "account-fingerprint",
        deviceId = "device-safe",
        deviceLabelId = "label-safe",
        expectedPolicy = "MIRROR",
        includeContactNames = false,
        lifecycleState = state.name,
        stage = FullMirrorPreviewStage.CHECKING_METADATA.name,
        createdAt = 1_000L,
        updatedAt = 9_000L,
        expiresAt = expiresAt,
        localScanComplete = true,
        localConversationCount = 3_335,
        localProcessed = 3_335,
        remoteDiscovered = 3_332,
        metadataChecked = metadataChecked,
        fullReadsCompleted = fullReadsCompleted,
        cachedUnchanged = metadataChecked
    )

    private class RecordingRecoveryGateway(
        private val works: MutableList<PreviewWorkSnapshot> = mutableListOf()
    ) : FullMirrorPreviewRecoveryGateway {
        var enqueueCount = 0
        var enqueuedScan: MirrorPreviewScanEntity? = null

        override suspend fun unique(name: String): List<PreviewWorkSnapshot> = works.toList()

        override suspend fun active(tag: String): List<PreviewWorkSnapshot> = emptyList()

        override fun enqueue(name: String, scan: MirrorPreviewScanEntity): UUID {
            enqueueCount += 1
            enqueuedScan = scan
            val id = UUID.randomUUID()
            works.removeAll { FullMirrorPreviewWorkContract.isActive(it.state) }
            works += PreviewWorkSnapshot(id, WorkInfo.State.ENQUEUED, scan.scanId, 0, 0)
            return id
        }
    }
}
