package io.github.isht1008.opensmsbackup.sms

data class SmsMessage(

    val id: Long,

    val threadId: Long = 0L,

    val address: String?,

    val contactName: String?,

    val body: String?,

    val date: Long,

    val dateFormatted: String,

    /**
     * Raw Android Telephony SMS type.
     *
     * Kept temporarily for compatibility with the existing JSON,
     * conversation and backup code.
     */
    val type: Int,

    val subscriptionId: Int? = null,

    val isRead: Boolean = true,

    val serviceCenter: String? = null
) {

    val smsType: SmsType
        get() = SmsType.fromAndroidType(type)
}
