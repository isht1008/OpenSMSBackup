package io.github.isht1008.opensmsbackup.sms

import android.provider.Telephony

enum class SmsType {

    RECEIVED,
    SENT,
    DRAFT,
    OUTBOX,
    FAILED,
    QUEUED,
    ALL,
    UNKNOWN;

    companion object {

        fun fromAndroidType(
            type: Int
        ): SmsType {

            return when (type) {

                Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX ->
                    RECEIVED

                Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT ->
                    SENT

                Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT ->
                    DRAFT

                Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX ->
                    OUTBOX

                Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED ->
                    FAILED

                Telephony.TextBasedSmsColumns.MESSAGE_TYPE_QUEUED ->
                    QUEUED

                Telephony.TextBasedSmsColumns.MESSAGE_TYPE_ALL ->
                    ALL

                else ->
                    UNKNOWN
            }
        }
    }
}