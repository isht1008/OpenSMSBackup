package io.github.isht1008.opensmsbackup.health

import io.github.isht1008.opensmsbackup.database.BackupVerificationEntity
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class BackupHealthFilter { ALL, MIRROR, ARCHIVE, VERIFIED, FAILED, LATEST_ACCOUNT }

data class BackupHealthStatistics(
    val totalRuns: Int = 0, val successfulRuns: Int = 0, val verifiedRuns: Int = 0,
    val averageIntegrity: Double = 0.0, val averageDurationMillis: Long = 0,
    val largestBackupMessages: Int = 0, val mostRecentAccount: String? = null,
    val mostRecentDevice: String? = null
)

data class ArchiveHealthPresentation(val title: String, val explanation: String, val level: String)

object BackupHealthCalculator {
    fun order(items: List<BackupVerificationEntity>) = items.sortedByDescending { it.completedAt }

    fun filter(
        items: List<BackupVerificationEntity>, filter: BackupHealthFilter, search: String
    ): List<BackupVerificationEntity> {
        val ordered = order(items)
        val latestAccount = ordered.firstOrNull()?.accountEmail
        val filtered = ordered.filter {
            when (filter) {
                BackupHealthFilter.ALL -> true
                BackupHealthFilter.MIRROR -> it.mode == "MIRROR"
                BackupHealthFilter.ARCHIVE -> it.mode == "ARCHIVE_APPEND_ONLY"
                BackupHealthFilter.VERIFIED -> it.status == "VERIFIED"
                BackupHealthFilter.FAILED -> it.status == "FAILED"
                BackupHealthFilter.LATEST_ACCOUNT -> it.accountEmail == latestAccount
            }
        }
        val query = search.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) return filtered
        return filtered.filter {
            it.accountEmail.lowercase(Locale.ROOT).contains(query) ||
                it.deviceName.lowercase(Locale.ROOT).contains(query) ||
                formatDate(it.completedAt).lowercase(Locale.ROOT).contains(query)
        }
    }

    fun statistics(items: List<BackupVerificationEntity>): BackupHealthStatistics {
        val ordered = order(items)
        if (ordered.isEmpty()) return BackupHealthStatistics()
        return BackupHealthStatistics(
            totalRuns = items.size,
            successfulRuns = items.count { it.status == "VERIFIED" || it.status == "PARTIALLY_VERIFIED" },
            verifiedRuns = items.count { it.status == "VERIFIED" },
            averageIntegrity = items.map { it.verificationPercent }.average(),
            averageDurationMillis = items.map { (it.completedAt - it.startedAt).coerceAtLeast(0) }.average().toLong(),
            largestBackupMessages = items.maxOf { it.localMessageCount },
            mostRecentAccount = ordered.first().accountEmail,
            mostRecentDevice = ordered.first().deviceName
        )
    }

    fun health(latest: BackupVerificationEntity?): ArchiveHealthPresentation = when (latest?.status) {
        "VERIFIED" -> ArchiveHealthPresentation("✓ Healthy", latest.shortSummary, "GREEN")
        "PARTIALLY_VERIFIED" -> ArchiveHealthPresentation("⚠ Needs attention", latest.shortSummary, "YELLOW")
        "FAILED" -> ArchiveHealthPresentation("✕ Verification failed", latest.shortSummary, "RED")
        "CANCELLED" -> ArchiveHealthPresentation("Verification cancelled", latest.shortSummary, "GRAY")
        else -> ArchiveHealthPresentation("Health not available", "Run backup verification to assess the selected Gmail archive.", "GRAY")
    }

    fun summary(item: BackupVerificationEntity): String = when (item.status) {
        "VERIFIED" -> "All ${item.localMessageCount} local messages were represented."
        "PARTIALLY_VERIFIED" -> "All current messages may be represented, but archive differences need review."
        "FAILED" -> "${item.missingMessageCount} current local messages were missing."
        "CANCELLED" -> "Verification stopped before the archive could be fully assessed."
        else -> "No valid verification scope was available."
    }

    fun formatDate(value: Long): String = DateFormat.getDateTimeInstance().format(Date(value))
    fun formatDuration(value: Long): String {
        val seconds = value.coerceAtLeast(0) / 1000
        val minutes = seconds / 60
        return if (minutes > 0) "${minutes}m ${seconds % 60}s" else "${seconds}s"
    }
}
