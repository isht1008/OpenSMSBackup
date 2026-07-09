package io.github.isht1008.opensmsbackup.util

import android.provider.Telephony

object SmsTypeUtils {

    fun getTypeName(type: Int): String =
        when (type) {
            Telephony.Sms.MESSAGE_TYPE_INBOX -> "RECEIVED"
            Telephony.Sms.MESSAGE_TYPE_SENT -> "SENT"
            Telephony.Sms.MESSAGE_TYPE_DRAFT -> "DRAFT"
            Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "OUTBOX"
            Telephony.Sms.MESSAGE_TYPE_FAILED -> "FAILED"
            Telephony.Sms.MESSAGE_TYPE_QUEUED -> "QUEUED"
            else -> "UNKNOWN"
        }
}