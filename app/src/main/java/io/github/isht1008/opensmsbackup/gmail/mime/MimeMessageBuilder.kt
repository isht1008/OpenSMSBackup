package io.github.isht1008.opensmsbackup.gmail.mime

import io.github.isht1008.opensmsbackup.gmail.header.OpenSmsHeaders
import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import io.github.isht1008.opensmsbackup.sms.SmsType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MimeMessageBuilder {

    companion object {

        private const val BACKUP_FORMAT_VERSION =
            "2"

        private const val UNKNOWN_CONTACT =
            "Unknown contact"

        private const val UNKNOWN_ADDRESS =
            "Unknown number"

        private const val MESSAGE_ID_DOMAIN =
            "opensmsbackup.invalid"

        fun messageIdForFingerprint(
            fingerprint: String
        ): String {

            val safeFingerprint =
                fingerprint
                    .filter { character ->
                        character.isLetterOrDigit() ||
                                character == '-' ||
                                character == '_'
                    }
                    .ifBlank {
                        fingerprint.hashCode()
                            .toUInt()
                            .toString(16)
                    }

            return "<opensmsbackup.$safeFingerprint@$MESSAGE_ID_DOMAIN>"
        }
    }

    fun build(
        sms: SmsMessage,
        accountEmail: String,
        fingerprint: String,
        parentMessageId: String? = null
    ): SmsEmail {

        require(accountEmail.isNotBlank()) {
            "Gmail account email cannot be blank."
        }

        require(fingerprint.isNotBlank()) {
            "SMS fingerprint cannot be blank."
        }

        val currentMessageId =
            messageIdForFingerprint(
                fingerprint
            )

        return SmsEmail(
            from = buildFrom(
                sms = sms,
                accountEmail = accountEmail
            ),
            to = buildTo(
                sms = sms,
                accountEmail = accountEmail
            ),
            subject = buildSubject(
                sms = sms
            ),
            body = buildBody(
                sms = sms
            ),
            date = sms.date,
            headers = buildHeaders(
                sms = sms,
                accountEmail = accountEmail,
                fingerprint = fingerprint,
                currentMessageId =
                    currentMessageId,
                parentMessageId =
                    parentMessageId
            )
        )
    }

    private fun buildFrom(
        sms: SmsMessage,
        accountEmail: String
    ): String {

        return when (sms.smsType) {

            SmsType.RECEIVED -> {
                buildParticipantAddress(
                    sms = sms,
                    accountEmail = accountEmail
                )
            }

            SmsType.SENT,
            SmsType.DRAFT,
            SmsType.OUTBOX,
            SmsType.FAILED,
            SmsType.QUEUED,
            SmsType.ALL,
            SmsType.UNKNOWN -> {
                accountEmail
            }
        }
    }

    private fun buildTo(
        sms: SmsMessage,
        accountEmail: String
    ): String {

        return when (sms.smsType) {

            SmsType.RECEIVED -> {
                accountEmail
            }

            SmsType.SENT,
            SmsType.DRAFT,
            SmsType.OUTBOX,
            SmsType.FAILED,
            SmsType.QUEUED,
            SmsType.ALL,
            SmsType.UNKNOWN -> {
                buildParticipantAddress(
                    sms = sms,
                    accountEmail = accountEmail
                )
            }
        }
    }

    private fun buildParticipantAddress(
        sms: SmsMessage,
        accountEmail: String
    ): String {

        val participant =
            buildParticipantDisplayName(
                sms = sms
            )

        return "\"${escapeDisplayName(participant)}\" <$accountEmail>"
    }

    private fun buildSubject(
        sms: SmsMessage
    ): String {

        val address =
            sms.address
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?: UNKNOWN_ADDRESS

        return "SMS • $address • Thread ${sms.threadId}"
    }

    private fun buildBody(
        sms: SmsMessage
    ): String {

        val messageBody =
            sms.body
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?: "(Empty SMS)"

        val direction =
            buildDirectionText(
                smsType = sms.smsType
            )

        val participant =
            buildParticipantDisplayName(
                sms = sms
            )

        val formattedDate =
            formatVisibleDate(
                timestamp = sms.date
            )

        return buildString {

            append(messageBody)
            appendLine()
            appendLine()

            append("— ")
            append(direction)

            if (
                participant.isNotBlank() &&
                participant != UNKNOWN_CONTACT
            ) {
                append(" • ")
                append(participant)
            }

            appendLine()

            append(formattedDate)
        }
    }

    private fun buildHeaders(
        sms: SmsMessage,
        accountEmail: String,
        fingerprint: String,
        currentMessageId: String,
        parentMessageId: String?
    ): Map<String, String> {

        return buildMap {

            put(
                "Message-ID",
                currentMessageId
            )

            if (!parentMessageId.isNullOrBlank()) {

                put(
                    "In-Reply-To",
                    parentMessageId
                )

                put(
                    "References",
                    parentMessageId
                )
            }

            put(
                OpenSmsHeaders.VERSION,
                BACKUP_FORMAT_VERSION
            )

            put(
                OpenSmsHeaders.SMS_ID,
                sms.id.toString()
            )

            put(
                OpenSmsHeaders.THREAD_ID,
                sms.threadId.toString()
            )

            put(
                OpenSmsHeaders.SMS_TYPE,
                sms.smsType.name
            )

            put(
                OpenSmsHeaders.PHONE_NUMBER,
                sanitizeHeaderValue(
                    sms.address.orEmpty()
                )
            )

            put(
                OpenSmsHeaders.CONTACT_NAME,
                sanitizeHeaderValue(
                    sms.contactName.orEmpty()
                )
            )

            put(
                OpenSmsHeaders.FINGERPRINT,
                sanitizeHeaderValue(
                    fingerprint
                )
            )

            put(
                OpenSmsHeaders.ACCOUNT,
                sanitizeHeaderValue(
                    accountEmail
                )
            )

            put(
                "X-OpenSMSBackup-Android-Type",
                sms.type.toString()
            )

            put(
                "X-OpenSMSBackup-Read",
                sms.isRead.toString()
            )

            put(
                "X-OpenSMSBackup-Date",
                sms.date.toString()
            )

            sms.subscriptionId
                ?.let { subscriptionId ->

                    put(
                        "X-OpenSMSBackup-Subscription-ID",
                        subscriptionId.toString()
                    )
                }

            sms.serviceCenter
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let { serviceCenter ->

                    put(
                        "X-OpenSMSBackup-Service-Center",
                        sanitizeHeaderValue(
                            serviceCenter
                        )
                    )
                }
        }
    }

    private fun buildParticipantDisplayName(
        sms: SmsMessage
    ): String {

        val contactName =
            sms.contactName
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }

        val address =
            sms.address
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }

        return when {

            contactName != null &&
                    address != null -> {

                "$contactName ($address)"
            }

            contactName != null -> {
                contactName
            }

            address != null -> {
                address
            }

            else -> {
                UNKNOWN_CONTACT
            }
        }
    }

    private fun buildDirectionText(
        smsType: SmsType
    ): String {

        return when (smsType) {

            SmsType.RECEIVED ->
                "Received"

            SmsType.SENT ->
                "Sent"

            SmsType.DRAFT ->
                "Draft"

            SmsType.OUTBOX ->
                "Outbox"

            SmsType.FAILED ->
                "Failed"

            SmsType.QUEUED ->
                "Queued"

            SmsType.ALL ->
                "SMS"

            SmsType.UNKNOWN ->
                "Unknown"
        }
    }

    private fun formatVisibleDate(
        timestamp: Long
    ): String {

        return SimpleDateFormat(
            "dd MMM yyyy, hh:mm:ss a",
            Locale.getDefault()
        ).format(
            Date(timestamp)
        )
    }

    private fun sanitizeHeaderValue(
        value: String
    ): String {

        return value
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
    }

    private fun escapeDisplayName(
        value: String
    ): String {

        return sanitizeHeaderValue(value)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

}