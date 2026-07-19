package io.github.isht1008.opensmsbackup.health

import io.github.isht1008.opensmsbackup.database.BackupVerificationEntity
import org.junit.Assert.*
import org.junit.Test

class BackupHealthCalculatorTest {
    @Test fun `history is newest first`() {
        assertEquals(listOf(3L, 2L, 1L), BackupHealthCalculator.order(
            listOf(item(1, 1), item(3, 3), item(2, 2))).map { it.id })
    }

    @Test fun `mode status latest account and search filters work`() {
        val values = listOf(
            item(1, 30, mode = "MIRROR", status = "VERIFIED", account = "new@example.com", device = "Work Phone"),
            item(2, 20, mode = "ARCHIVE_APPEND_ONLY", status = "FAILED", account = "old@example.com"),
            item(3, 10, mode = "MIRROR", status = "VERIFIED", account = "new@example.com")
        )
        assertEquals(2, BackupHealthCalculator.filter(values, BackupHealthFilter.MIRROR, "").size)
        assertEquals(1, BackupHealthCalculator.filter(values, BackupHealthFilter.ARCHIVE, "").size)
        assertEquals(2, BackupHealthCalculator.filter(values, BackupHealthFilter.VERIFIED, "").size)
        assertEquals(1, BackupHealthCalculator.filter(values, BackupHealthFilter.FAILED, "").size)
        assertEquals(2, BackupHealthCalculator.filter(values, BackupHealthFilter.LATEST_ACCOUNT, "").size)
        assertEquals(1, BackupHealthCalculator.filter(values, BackupHealthFilter.ALL, "work phone").size)
        assertEquals(1, BackupHealthCalculator.filter(values, BackupHealthFilter.ALL, "old@example.com").size)
    }

    @Test fun `statistics are calculated without weighting integrity by message count`() {
        val values = listOf(
            item(1, 10, status = "VERIFIED", percent = 100.0, messages = 100, duration = 1_000),
            item(2, 20, status = "PARTIALLY_VERIFIED", percent = 80.0, messages = 500, duration = 3_000),
            item(3, 30, status = "FAILED", percent = 50.0, messages = 20, duration = 2_000,
                account = "latest@example.com", device = "Latest Phone")
        )
        val result = BackupHealthCalculator.statistics(values)
        assertEquals(3, result.totalRuns); assertEquals(2, result.successfulRuns)
        assertEquals(1, result.verifiedRuns); assertEquals(76.666, result.averageIntegrity, 0.01)
        assertEquals(2_000, result.averageDurationMillis); assertEquals(500, result.largestBackupMessages)
        assertEquals("latest@example.com", result.mostRecentAccount); assertEquals("Latest Phone", result.mostRecentDevice)
    }

    @Test fun `health and summaries remain conservative`() {
        assertEquals("✓ Healthy", BackupHealthCalculator.health(item(1, 1, status = "VERIFIED")).title)
        assertEquals("⚠ Needs attention", BackupHealthCalculator.health(item(1, 1, status = "PARTIALLY_VERIFIED")).title)
        assertEquals("✕ Verification failed", BackupHealthCalculator.health(item(1, 1, status = "FAILED")).title)
        assertTrue(BackupHealthCalculator.summary(item(1, 1, status = "FAILED", missing = 4)).contains("4"))
        assertEquals("1m 5s", BackupHealthCalculator.formatDuration(65_000))
    }

    private fun item(
        id: Long, completed: Long, mode: String = "MIRROR", status: String = "VERIFIED",
        percent: Double = 100.0, messages: Int = 10, duration: Long = 1_000,
        account: String = "user@example.com", device: String = "Phone", missing: Int = 0
    ) = BackupVerificationEntity(id, "p", account, "d", device, mode,
        completed - duration, completed, status, messages, 2, messages, 2,
        messages - missing, missing, 0, 0, 0, percent, "summary")
}
