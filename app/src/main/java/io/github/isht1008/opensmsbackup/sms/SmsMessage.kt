package io.github.isht1008.opensmsbackup.sms

data class SmsMessage(
    val id: String,

    // Sender phone number or SMS sender ID
    val address: String?,

    // Contact name from Android Contacts (null if not found)
    val contactName: String?,

    // SMS text
    val body: String?,

    // Unix timestamp in milliseconds
    val date: Long,

    // Telephony.Sms.MESSAGE_TYPE_INBOX, SENT, etc.
    val type: Int
)