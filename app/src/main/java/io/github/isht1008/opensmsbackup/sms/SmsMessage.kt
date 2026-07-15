package io.github.isht1008.opensmsbackup.sms

data class SmsMessage(

    val id: Long,

    val threadId: Long = 0L,

    val address: String?,

    val contactName: String?,

    val body: String?,

    val date: Long,

    val dateFormatted: String,

    val type: Int,

    val subscriptionId: Int? = null,

    val isRead: Boolean = true
)

