package io.github.isht1008.opensmsbackup.verification

import io.github.isht1008.opensmsbackup.gmail.backup.SmsFingerprint
import io.github.isht1008.opensmsbackup.gmail.backup.SmsFingerprintVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun interface VerificationArchiveRepository {
    suspend fun loadArchive(request: BackupVerificationRequest): Result<VerificationArchiveSnapshot>
}

interface BackupVerificationEngine {
    suspend fun verify(request: BackupVerificationRequest, onProgress: suspend (BackupVerificationProgress) -> Unit = {}): BackupVerificationResult
}

class DefaultBackupVerificationEngine(
    private val repository: VerificationArchiveRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : BackupVerificationEngine {
    override suspend fun verify(
        request: BackupVerificationRequest,
        onProgress: suspend (BackupVerificationProgress) -> Unit
    ): BackupVerificationResult {
        val started = clock()
        fun empty(status: BackupVerificationStatus, issues: List<BackupVerificationIssue>, unreadable: Int = 0) =
            BackupVerificationResult(request.profileId, request.accountEmail, request.deviceId,
                request.deviceDisplayName, request.mode, started, clock(), request.localMessages.size,
                request.localMessages.map { it.threadId }.toSet().size, 0, 0, 0,
                request.localMessages.size, 0, 0, unreadable, 0.0, status, issues)
        try {
            onProgress(BackupVerificationProgress(VerificationStage.DISCOVERING_ARCHIVE, 0, null))
            val archive = repository.loadArchive(request).getOrElse {
                return empty(BackupVerificationStatus.NOT_AVAILABLE,
                    listOf(BackupVerificationIssue(BackupVerificationIssueType.ARCHIVE_LOOKUP_FAILED)))
            }
            if (archive.messages.isEmpty() && archive.conversationCount == 0) {
                val issues = archive.issues + if (archive.unreadableArchiveCount > 0) listOf(
                    BackupVerificationIssue(BackupVerificationIssueType.UNREADABLE_ARCHIVE, archive.unreadableArchiveCount)
                ) else emptyList()
                return empty(BackupVerificationStatus.NOT_AVAILABLE, issues, archive.unreadableArchiveCount)
            }
            onProgress(BackupVerificationProgress(VerificationStage.INDEXING_MESSAGES, 0, archive.messages.size))
            val aliasToRecords = HashMap<String, MutableList<Int>>()
            val canonicalCounts = HashMap<String, Int>()
            archive.messages.forEachIndexed { index, message ->
                if (index % CANCELLATION_INTERVAL == 0) currentCoroutineContext().ensureActive()
                val legacy = SmsFingerprint.generate(message)
                val canonical = SmsFingerprint.generate(
                    message,
                    SmsFingerprintVersion.V2_COUNTRY_AWARE,
                    request.defaultRegion
                )
                sequenceOf(legacy, canonical).distinct().forEach {
                    aliasToRecords.getOrPut(it) { ArrayList(1) }.add(index)
                }
                canonicalCounts[canonical] = (canonicalCounts[canonical] ?: 0) + 1
            }
            val duplicates = canonicalCounts.values.sumOf { (it - 1).coerceAtLeast(0) }
            val consumed = BooleanArray(archive.messages.size)
            var matched = 0
            onProgress(BackupVerificationProgress(VerificationStage.COMPARING, 0, request.localMessages.size))
            request.localMessages.forEachIndexed { index, local ->
                if (index % CANCELLATION_INTERVAL == 0) currentCoroutineContext().ensureActive()
                val record = SmsFingerprint.aliases(local, request.defaultRegion).asSequence()
                    .flatMap { aliasToRecords[it].orEmpty().asSequence() }
                    .firstOrNull { !consumed[it] }
                if (record != null) { consumed[record] = true; matched++ }
                if (index % 250 == 0) onProgress(BackupVerificationProgress(
                    VerificationStage.COMPARING, index + 1, request.localMessages.size))
            }
            val missing = request.localMessages.size - matched
            val unexpected = consumed.count { !it }
            val issues = archive.issues.toMutableList().apply {
                if (missing > 0) add(BackupVerificationIssue(BackupVerificationIssueType.MISSING_MESSAGE, missing))
                if (duplicates > 0) add(BackupVerificationIssue(BackupVerificationIssueType.DUPLICATE_ARCHIVE_MESSAGE, duplicates))
                if (archive.unreadableArchiveCount > 0) add(BackupVerificationIssue(
                    BackupVerificationIssueType.UNREADABLE_ARCHIVE, archive.unreadableArchiveCount))
            }
            val percent = if (request.localMessages.isEmpty()) 100.0 else matched * 100.0 / request.localMessages.size
            val critical = archive.unreadableArchiveCount > 0 || !archive.complete || archive.issues.isNotEmpty()
            val status = when {
                missing > 0 -> BackupVerificationStatus.FAILED
                critical || unexpected > 0 || duplicates > 0 -> BackupVerificationStatus.PARTIALLY_VERIFIED
                else -> BackupVerificationStatus.VERIFIED
            }
            onProgress(BackupVerificationProgress(VerificationStage.FINALIZING, matched, request.localMessages.size))
            return BackupVerificationResult(request.profileId, request.accountEmail, request.deviceId,
                request.deviceDisplayName, request.mode, started, clock(), request.localMessages.size,
                request.localMessages.map { it.threadId }.toSet().size, archive.messages.size,
                archive.conversationCount, matched, missing, unexpected, duplicates,
                archive.unreadableArchiveCount, percent, status, issues)
        } catch (_: CancellationException) {
            return empty(BackupVerificationStatus.CANCELLED, emptyList())
        }
    }

    private companion object {
        const val CANCELLATION_INTERVAL = 250
    }
}
