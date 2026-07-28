package io.github.isht1008.opensmsbackup.gmail.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailBackupEtaEstimatorTest {
    @Test fun `eta calculates after two samples and never reaches zero while work remains`() {
        var now = 1_000L
        val estimator = GmailBackupEtaEstimator(1_000L) { now }
        assertNull(estimator.update(GmailBackupPhase.COMPARING, 0, 4).remainingSeconds)
        now += 2_000L
        assertNull(estimator.update(GmailBackupPhase.COMPARING, 1, 4).remainingSeconds)
        now += 2_000L
        val estimate = estimator.update(GmailBackupPhase.COMPARING, 2, 4)
        assertEquals(4L, estimate.remainingSeconds)
        assertTrue(requireNotNull(estimate.remainingSeconds) > 0L)
    }

    @Test fun `phase change returns to calculating and terminal stops eta`() {
        var now = 5_000L
        val estimator = GmailBackupEtaEstimator(5_000L) { now }
        estimator.update(GmailBackupPhase.COMPARING, 0, 5)
        now += 1_000L
        estimator.update(GmailBackupPhase.COMPARING, 1, 5)
        now += 1_000L
        assertTrue(estimator.update(GmailBackupPhase.COMPARING, 2, 5).remainingSeconds != null)
        assertNull(estimator.update(GmailBackupPhase.INDEXING, 2, 5).remainingSeconds)
        now += 500L
        val terminal = estimator.update(GmailBackupPhase.FINALIZING, 2, 5, terminal = true)
        assertNull(terminal.remainingSeconds)
        assertEquals(2_500L, terminal.elapsedMillis)
    }

    @Test fun `monotonic clock cannot produce negative elapsed time`() {
        val estimator = GmailBackupEtaEstimator(10_000L) { 9_000L }
        assertEquals(
            0L,
            estimator.update(GmailBackupPhase.READING, 0, 1).elapsedMillis
        )
    }
}
