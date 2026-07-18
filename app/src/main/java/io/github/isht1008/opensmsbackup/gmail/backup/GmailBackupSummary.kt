package io.github.isht1008.opensmsbackup.gmail.backup

data class GmailBackupSummary(

    val totalMessages: Int,

    val totalConversations: Int,

    val checkedConversations: Int,

    val uploadedConversations: Int,

    val skippedConversations: Int,

    val failedConversations: Int,

    val stoppedAtSafetyLimit: Boolean,

    val failures: List<String>,

    val warnings: List<String>
)
