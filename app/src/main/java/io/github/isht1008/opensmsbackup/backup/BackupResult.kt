package io.github.isht1008.opensmsbackup.backup

import io.github.isht1008.opensmsbackup.model.Conversation

data class BackupResult(
    val totalMessages: Int,
    val totalConversations: Int,
    val conversations: List<Conversation>
)