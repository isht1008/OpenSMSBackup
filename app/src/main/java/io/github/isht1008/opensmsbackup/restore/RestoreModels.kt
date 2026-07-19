package io.github.isht1008.opensmsbackup.restore

import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshot
import io.github.isht1008.opensmsbackup.sms.SmsType

data class RestoreSourceDevice(val deviceId: String, val displayName: String, val labelId: String)

data class RestoreConversation(
    val key: String,
    val snapshot: SmsConversationSnapshot,
    val internalDate: Long
) {
    val searchableText: String = listOfNotNull(snapshot.contactName, snapshot.address)
        .joinToString(" ").lowercase()
}

data class RestorePreview(
    val profileId: String,
    val accountEmail: String,
    val sourceDevice: RestoreSourceDevice,
    val conversations: List<RestoreConversation>,
    val estimatedDuplicates: Int
) {
    val messageCount = conversations.sumOf { it.snapshot.messageCount }
    val receivedCount = conversations.sumOf { conversation ->
        conversation.snapshot.messages.count { it.smsType == SmsType.RECEIVED }
    }
    val sentCount = messageCount - receivedCount
    val earliestDate = conversations.flatMap { it.snapshot.messages }.minOfOrNull { it.date }
    val latestDate = conversations.flatMap { it.snapshot.messages }.maxOfOrNull { it.date }
}

data class RestoreResult(
    val selected: Int,
    val restored: Int,
    val skippedDuplicates: Int,
    val failed: Int,
    val cancelled: Boolean
)
