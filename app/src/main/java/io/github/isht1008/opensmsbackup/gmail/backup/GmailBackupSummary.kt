package io.github.isht1008.opensmsbackup.gmail.backup

data class GmailBackupSummary(

    val totalMessages: Int,

    val checkedMessages: Int,

    val uploadedMessages: Int,

    val skippedMessages: Int,

    val failedMessages: Int,

    val stoppedAtSafetyLimit: Boolean,

    val failures: List<String>
)