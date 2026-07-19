package io.github.isht1008.opensmsbackup.restore

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import io.github.isht1008.opensmsbackup.sms.SmsType

class AndroidSmsRestoreWriter(private val context: Context) : SmsRestoreWriter {
    override suspend fun insert(message: SmsMessage): Result<Unit> = runCatching {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, message.address)
            put(Telephony.Sms.BODY, message.body)
            put(Telephony.Sms.DATE, message.date)
            put(Telephony.Sms.DATE_SENT, message.date)
            put(Telephony.Sms.TYPE, if (message.smsType == SmsType.SENT) {
                Telephony.Sms.MESSAGE_TYPE_SENT
            } else Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.READ, if (message.isRead) 1 else 0)
        }
        val destination = if (message.smsType == SmsType.SENT) {
            Telephony.Sms.Sent.CONTENT_URI
        } else Telephony.Sms.Inbox.CONTENT_URI
        checkNotNull(context.contentResolver.insert(destination, values))
    }
}
