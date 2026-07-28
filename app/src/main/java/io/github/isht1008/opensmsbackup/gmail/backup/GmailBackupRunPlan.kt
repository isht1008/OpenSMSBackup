package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.account.data.GmailBackupMode

data class GmailBackupRunPlan(
    val conversations: List<SmsConversationSnapshot>,
    val sourceConversationCount: Int,
    val scope: GmailBackupScope,
    val blockedReason: String? = null
) {
    val isLimitedTest: Boolean
        get() = scope == GmailBackupScope.RECENT_TEST
}

object GmailBackupRunPlanner {
    const val FULL_MIRROR_BLOCK_REASON =
        "Full Mirror backup is not available yet. It requires a deletion preview, " +
            "thresholds, confirmation, and recovery safeguards. No backup was started."

    fun create(
        allConversations: List<SmsConversationSnapshot>,
        scope: GmailBackupScope,
        mode: GmailBackupMode
    ): GmailBackupRunPlan {
        val selected = GmailBackupConversationLimiter.applyConversations(
            orderedConversations = allConversations,
            scope = scope
        )
        return GmailBackupRunPlan(
            conversations = selected.conversations,
            sourceConversationCount = selected.sourceConversationCount,
            scope = scope,
            blockedReason = FULL_MIRROR_BLOCK_REASON.takeIf {
                scope != GmailBackupScope.RECENT_TEST && mode == GmailBackupMode.MIRROR
            }
        )
    }

    fun completionState(
        scope: GmailBackupScope,
        abortState: GmailBackupCompletionState?,
        failed: Int
    ): GmailBackupCompletionState =
        abortState ?: when {
            scope == GmailBackupScope.RECENT_TEST ->
                GmailBackupCompletionState.LIMITED_TEST_COMPLETED
            failed > 0 -> GmailBackupCompletionState.COMPLETED_WITH_FAILURES
            else -> GmailBackupCompletionState.COMPLETED
        }

    fun shouldAdvanceFullBackupMetadata(
        scope: GmailBackupScope,
        abortState: GmailBackupCompletionState?,
        failed: Int
    ): Boolean =
        scope == GmailBackupScope.FULL && abortState == null && failed == 0
}
