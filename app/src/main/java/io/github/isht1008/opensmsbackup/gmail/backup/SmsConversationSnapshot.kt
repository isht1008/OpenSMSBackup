package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.sms.SmsMessage

data class SmsConversationSnapshot(

    val threadId: Long,

    val address: String?,

    val contactName: String?,

    val messages: List<SmsMessage>
) {

    val messageCount: Int
        get() = messages.size

    val firstMessageDate: Long
        get() = messages.firstOrNull()?.date ?: 0L

    val lastMessageDate: Long
        get() = messages.lastOrNull()?.date ?: 0L
}
