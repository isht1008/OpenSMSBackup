package io.github.isht1008.opensmsbackup.gmail.backup

import java.security.MessageDigest

/** Temporary physical-device test configuration. Set to null to restore full backups. */
object GmailBackupTestConfig {
    val MAX_CONVERSATIONS_PER_BACKUP: Int? = 10

}

data class GmailBackupConversationScope<T>(
    val conversations: List<T>,
    val sourceConversationCount: Int,
    val isLimitedTest: Boolean
)

object GmailBackupConversationLimiter {
    fun applyConversations(
        orderedConversations: List<SmsConversationSnapshot>,
        maximumConversations: Int? = GmailBackupTestConfig.MAX_CONVERSATIONS_PER_BACKUP
    ): GmailBackupConversationScope<SmsConversationSnapshot> {
        require(maximumConversations == null || maximumConversations > 0) {
            "Conversation limit must be greater than zero."
        }
        val recentFirst = orderedConversations.sortedWith(
            compareByDescending<SmsConversationSnapshot> { it.lastMessageDate }
                .thenByDescending { it.threadId }
        )
        val selected = maximumConversations?.let(recentFirst::take) ?: recentFirst
        return GmailBackupConversationScope(
            conversations = selected,
            sourceConversationCount = orderedConversations.size,
            isLimitedTest = maximumConversations != null
        )
    }

    fun safeIdentity(canonicalAddress: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(canonicalAddress.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
}

object GmailBackupTestModePolicy {
    /** Mirror is safe here because it operates only on explicitly supplied conversations. */
    fun supports(mode: io.github.isht1008.opensmsbackup.account.data.GmailBackupMode): Boolean =
        mode == io.github.isht1008.opensmsbackup.account.data.GmailBackupMode.MIRROR ||
            mode == io.github.isht1008.opensmsbackup.account.data.GmailBackupMode.ARCHIVE_APPEND_ONLY

    fun shouldAdvanceFullBackupMetadata(
        isLimitedTest: Boolean,
        aborted: Boolean
    ): Boolean = !isLimitedTest && !aborted
}
