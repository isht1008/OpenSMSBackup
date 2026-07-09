package io.github.isht1008.opensmsbackup.model

import io.github.isht1008.opensmsbackup.sms.SmsMessage

data class Conversation(

    // Phone number or sender ID
    val address: String,

    // Contact name if available
    val contactName: String?,

    // All messages belonging to this conversation
    val messages: MutableList<SmsMessage> = mutableListOf()
)