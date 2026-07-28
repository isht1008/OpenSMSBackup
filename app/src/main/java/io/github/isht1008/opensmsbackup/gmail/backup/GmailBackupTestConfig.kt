package io.github.isht1008.opensmsbackup.gmail.backup

import java.security.MessageDigest

enum class GmailBackupScope(val conversationLimit: Int?) {
    INCREMENTAL(null),
    FULL(null),
    RECENT_TEST(10)
}

data class GmailBackupConversationScope<T>(
    val conversations: List<T>,
    val sourceConversationCount: Int,
    val scope: GmailBackupScope
) {
    val isLimitedTest: Boolean
        get() = scope == GmailBackupScope.RECENT_TEST
}

object GmailBackupConversationLimiter {
    fun applyConversations(
        orderedConversations: List<SmsConversationSnapshot>,
        scope: GmailBackupScope
    ): GmailBackupConversationScope<SmsConversationSnapshot> {
        val recentFirst = orderedConversations.sortedWith(
            compareByDescending<SmsConversationSnapshot> { it.lastMessageDate }
                .thenByDescending { it.threadId }
        )
        val selected = scope.conversationLimit?.let(recentFirst::take) ?: recentFirst
        return GmailBackupConversationScope(
            conversations = selected,
            sourceConversationCount = orderedConversations.size,
            scope = scope
        )
    }

    fun safeIdentity(canonicalAddress: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(canonicalAddress.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
}
