package io.github.isht1008.opensmsbackup.verification

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode
import io.github.isht1008.opensmsbackup.sms.SmsMessage

enum class BackupVerificationStatus { VERIFIED, PARTIALLY_VERIFIED, FAILED, NOT_AVAILABLE, CANCELLED }
enum class BackupVerificationIssueType {
    MISSING_MESSAGE, DUPLICATE_ARCHIVE_MESSAGE, UNREADABLE_ARCHIVE, WRONG_ACCOUNT,
    WRONG_DEVICE, INVALID_CONVERSATION_IDENTITY, UNSUPPORTED_ARCHIVE_VERSION,
    ARCHIVE_LOOKUP_FAILED, SAFETY_LIMIT_REACHED
}
data class BackupVerificationIssue(val type: BackupVerificationIssueType, val count: Int = 1)
enum class VerificationStage { READING_LOCAL_MESSAGES, DISCOVERING_ARCHIVE, READING_ARCHIVE, INDEXING_MESSAGES, COMPARING, FINALIZING }
data class BackupVerificationProgress(val stage: VerificationStage, val processed: Int, val total: Int?, val message: String? = null)

data class BackupVerificationRequest(
    val profileId: String, val accountEmail: String, val deviceId: String,
    val deviceDisplayName: String, val mode: GmailBackupMode, val defaultRegion: String,
    val localMessages: List<SmsMessage>,
    val completeLocalScope: Boolean = true
)

data class VerificationArchiveSnapshot(
    val messages: List<SmsMessage>, val conversationCount: Int,
    val unreadableArchiveCount: Int = 0, val issues: List<BackupVerificationIssue> = emptyList(),
    val complete: Boolean = true
)

data class BackupVerificationResult(
    val profileId: String, val accountEmail: String, val deviceId: String,
    val deviceDisplayName: String, val mode: GmailBackupMode,
    val startedAt: Long, val completedAt: Long,
    val localMessageCount: Int, val localConversationCount: Int,
    val archivedMessageCount: Int, val archivedConversationCount: Int,
    val matchedMessageCount: Int, val missingMessageCount: Int,
    val unexpectedArchivedMessageCount: Int, val duplicateFingerprintCount: Int,
    val unreadableArchiveCount: Int, val verificationPercent: Double,
    val status: BackupVerificationStatus, val issues: List<BackupVerificationIssue>
)
