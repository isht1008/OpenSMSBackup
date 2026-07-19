package io.github.isht1008.opensmsbackup.gmail.mime

import io.github.isht1008.opensmsbackup.gmail.backup.SmsConversationSnapshot
import io.github.isht1008.opensmsbackup.gmail.backup.ArchiveConversationIdentity
import io.github.isht1008.opensmsbackup.device.DeviceProfile
import io.github.isht1008.opensmsbackup.device.DeviceDisplayName
import io.github.isht1008.opensmsbackup.gmail.header.OpenSmsHeaders
import io.github.isht1008.opensmsbackup.gmail.model.EmailAttachment
import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import io.github.isht1008.opensmsbackup.sms.SmsType
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

class ConversationMimeMessageBuilder {

    companion object {

        private const val BACKUP_FORMAT_VERSION =
            "3"

        private const val UNKNOWN_CONTACT =
            "Unknown contact"

        private const val UNKNOWN_ADDRESS =
            "Unknown number"

        private const val MESSAGE_ID_DOMAIN =
            "opensmsbackup.invalid"

        private const val FROM_DOMAIN =
            "opensmsbackup.noreply"

        private const val MAX_LOCAL_PART_LENGTH =
            64
    }

    fun build(
        conversation: SmsConversationSnapshot,
        accountEmail: String,
        snapshotHash: String,
        deviceProfile: DeviceProfile? = null
    ): SmsEmail {

        require(accountEmail.isNotBlank()) {
            "Gmail account email cannot be blank."
        }

        require(snapshotHash.isNotBlank()) {
            "Conversation snapshot hash cannot be blank."
        }

        val attachmentName =
            "opensms-conversation-${conversation.threadId}.json"

        return SmsEmail(
            from = buildFromAddress(
                conversation
            ),
            to = accountEmail,
            subject = buildSubject(
                conversation
            ),
            body = buildPlainTextBody(
                conversation = conversation,
                attachmentName = attachmentName
            ),
            date = conversation.lastMessageDate
                .takeIf { date ->
                    date > 0L
                }
                ?: System.currentTimeMillis(),
            headers = buildHeaders(
                conversation = conversation,
                accountEmail = accountEmail,
                snapshotHash = snapshotHash,
                deviceProfile = deviceProfile
            ),
            htmlBody = buildHtmlBody(
                conversation = conversation,
                attachmentName = attachmentName
            ),
            attachments = listOf(
                EmailAttachment(
                    fileName = attachmentName,
                    mimeType =
                        "application/vnd.opensmsbackup.conversation+json",
                    content = buildArchiveJson(
                        conversation = conversation,
                        accountEmail = accountEmail,
                        snapshotHash = snapshotHash,
                        deviceProfile = deviceProfile
                    ).toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
            )
        )
    }

    private fun buildFromAddress(
        conversation: SmsConversationSnapshot
    ): String {

        val displayName =
            buildParticipantDisplayName(
                conversation
            )

        val localPartSource =
            conversation.contactName
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?: conversation.address
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
                ?: "conversation-${conversation.threadId}"

        val localPart =
            sanitizeEmailLocalPart(
                value = localPartSource,
                threadId = conversation.threadId
            )

        return "${encodeDisplayName(displayName)} <$localPart@$FROM_DOMAIN>"
    }

    private fun buildSubject(
        conversation: SmsConversationSnapshot
    ): String {

        val participant =
            buildParticipantDisplayName(
                conversation
            )

        val messageText =
            if (conversation.messageCount == 1) {
                "1 SMS"
            } else {
                "${conversation.messageCount} SMS messages"
            }

        return "$participant • $messageText"
    }

    private fun buildPlainTextBody(
        conversation: SmsConversationSnapshot,
        attachmentName: String
    ): String {

        val participant =
            buildParticipantDisplayName(
                conversation
            )

        val address =
            conversation.address
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }

        return buildString {

            appendLine(participant)

            if (
                address != null &&
                !participant.contains(address)
            ) {
                appendLine(address)
            }

            appendLine(
                buildConversationSummary(
                    conversation
                )
            )

            appendLine()
            appendLine(
                "────────────────────────────────"
            )

            conversation.messages.forEachIndexed {
                    index,
                    message ->

                appendLine()

                appendLine(
                    buildMessageAuthor(
                        message = message,
                        participant = participant
                    )
                )

                appendLine(
                    "${buildDirectionText(message.smsType)} • ${formatVisibleDate(message.date)}"
                )

                appendLine()

                appendLine(
                    message.body
                        ?.takeIf { body ->
                            body.isNotEmpty()
                        }
                        ?: "(Empty SMS)"
                )

                if (index != conversation.messages.lastIndex) {
                    appendLine()
                    appendLine(
                        "────────────────────────────────"
                    )
                }
            }

            appendLine()
            appendLine()
            appendLine(
                "Archive data: $attachmentName"
            )

            append(
                "Created by OpenSMS Backup"
            )
        }
    }

    private fun buildHtmlBody(
        conversation: SmsConversationSnapshot,
        attachmentName: String
    ): String {

        val participant =
            buildParticipantDisplayName(
                conversation
            )

        val address =
            conversation.address
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }

        return buildString {

            append("<!doctype html>")
            append("<html><body style=\"margin:0;padding:0;background:#f6f8fb;\">")

            append(
                "<div style=\"max-width:760px;margin:0 auto;padding:24px 12px;" +
                        "font-family:Arial,Helvetica,sans-serif;color:#202124;\">"
            )

            append(
                "<div style=\"background:#ffffff;border:1px solid #e0e3e7;" +
                        "border-radius:16px;padding:22px 24px;margin-bottom:18px;\">"
            )

            append(
                "<div style=\"font-size:12px;font-weight:700;letter-spacing:.08em;" +
                        "text-transform:uppercase;color:#5f6368;margin-bottom:8px;\">" +
                        "SMS conversation</div>"
            )

            append(
                "<div style=\"font-size:24px;font-weight:700;line-height:1.3;" +
                        "word-break:break-word;\">${escapeHtml(participant)}</div>"
            )

            if (
                address != null &&
                !participant.contains(address)
            ) {
                append(
                    "<div style=\"font-size:14px;color:#5f6368;margin-top:5px;\">" +
                            "${escapeHtml(address)}</div>"
                )
            }

            append(
                "<div style=\"font-size:14px;color:#5f6368;margin-top:12px;\">" +
                        "${escapeHtml(buildConversationSummary(conversation))}</div>"
            )

            append("</div>")

            conversation.messages.forEach { message ->

                val sent =
                    message.smsType == SmsType.SENT

                val alignment =
                    if (sent) {
                        "right"
                    } else {
                        "left"
                    }

                val bubbleBackground =
                    if (sent) {
                        "#dbeafe"
                    } else {
                        "#ffffff"
                    }

                val borderColor =
                    if (sent) {
                        "#bfdbfe"
                    } else {
                        "#e0e3e7"
                    }

                val author =
                    buildMessageAuthor(
                        message = message,
                        participant = participant
                    )

                val messageBody =
                    message.body
                        ?.takeIf { body ->
                            body.isNotEmpty()
                        }
                        ?: "(Empty SMS)"

                append(
                    "<div style=\"text-align:$alignment;margin:0 0 18px 0;\">"
                )

                append(
                    "<div style=\"font-size:12px;color:#5f6368;margin:0 8px 6px 8px;\">" +
                            "<strong>${escapeHtml(author)}</strong> &nbsp;•&nbsp; " +
                            "${escapeHtml(formatVisibleDate(message.date))}</div>"
                )

                append(
                    "<div style=\"display:inline-block;max-width:82%;text-align:left;" +
                            "background:$bubbleBackground;border:1px solid $borderColor;" +
                            "border-radius:16px;padding:12px 15px;font-size:15px;" +
                            "line-height:1.55;white-space:normal;word-break:break-word;\">" +
                            "${messageBodyToHtml(messageBody)}</div>"
                )

                append(
                    "<div style=\"font-size:11px;color:#80868b;margin:5px 10px 0 10px;\">" +
                            "${escapeHtml(buildDirectionText(message.smsType))}</div>"
                )

                append("</div>")
            }

            append(
                "<div style=\"background:#ffffff;border:1px solid #e0e3e7;" +
                        "border-radius:12px;padding:14px 16px;margin-top:8px;" +
                        "font-size:12px;line-height:1.5;color:#5f6368;\">"
            )

            append(
                "Archive data is attached as <strong>${escapeHtml(attachmentName)}</strong>.<br>"
            )

            append(
                "Created by OpenSMS Backup"
            )

            append("</div>")
            append("</div>")
            append("</body></html>")
        }
    }

    private fun buildParticipantDisplayName(
        conversation: SmsConversationSnapshot
    ): String {

        val contactName =
            conversation.contactName
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }

        val address =
            conversation.address
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

    private fun buildConversationSummary(
        conversation: SmsConversationSnapshot
    ): String {

        val messageText =
            if (conversation.messageCount == 1) {
                "1 message"
            } else {
                "${conversation.messageCount} messages"
            }

        if (conversation.messages.isEmpty()) {
            return messageText
        }

        return "$messageText • " +
                "${formatDateOnly(conversation.firstMessageDate)} – " +
                "${formatDateOnly(conversation.lastMessageDate)}"
    }

    private fun buildMessageAuthor(
        message: SmsMessage,
        participant: String
    ): String {

        return when (message.smsType) {
            SmsType.SENT ->
                "You"

            SmsType.RECEIVED ->
                participant

            SmsType.DRAFT ->
                "Draft"

            SmsType.OUTBOX ->
                "You"

            SmsType.FAILED ->
                "You"

            SmsType.QUEUED ->
                "You"

            SmsType.ALL ->
                participant

            SmsType.UNKNOWN ->
                participant
        }
    }

    private fun buildHeaders(
        conversation: SmsConversationSnapshot,
        accountEmail: String,
        snapshotHash: String,
        deviceProfile: DeviceProfile?
    ): Map<String, String> {

        val messageId =
            "<opensmsbackup.conversation.${conversation.threadId}.$snapshotHash@$MESSAGE_ID_DOMAIN>"

        return buildMap {

            put(
                "Message-ID",
                messageId
            )

            put(
                OpenSmsHeaders.VERSION,
                BACKUP_FORMAT_VERSION
            )

            put(
                OpenSmsHeaders.CONTENT_TYPE,
                "conversation-snapshot"
            )

            put(
                OpenSmsHeaders.THREAD_ID,
                conversation.threadId.toString()
            )

            put(
                OpenSmsHeaders.PHONE_NUMBER,
                sanitizeHeaderValue(
                    conversation.address.orEmpty()
                )
            )

            put(
                OpenSmsHeaders.CONTACT_NAME,
                sanitizeHeaderValue(
                    conversation.contactName.orEmpty()
                )
            )

            put(
                OpenSmsHeaders.MESSAGE_COUNT,
                conversation.messageCount.toString()
            )

            put(
                OpenSmsHeaders.FIRST_MESSAGE_DATE,
                conversation.firstMessageDate.toString()
            )

            put(
                OpenSmsHeaders.LAST_MESSAGE_DATE,
                conversation.lastMessageDate.toString()
            )

            put(
                OpenSmsHeaders.SNAPSHOT_HASH,
                snapshotHash
            )

            put(
                OpenSmsHeaders.CONVERSATION_KEY,
                if (deviceProfile == null) {
                    ArchiveConversationIdentity.key(conversation.address, accountEmail)
                } else {
                    ArchiveConversationIdentity.key(
                        ArchiveConversationIdentity.Version.V3_ACCOUNT_DEVICE_COUNTRY_ADDRESS,
                        conversation.address,
                        accountEmail,
                        deviceProfile.deviceId,
                        deviceProfile.defaultRegion
                    )
                }
            )

            if (deviceProfile != null) {
                put(OpenSmsHeaders.ARCHIVE_IDENTITY_VERSION, "3")
                put(OpenSmsHeaders.DEVICE_ID, sanitizeHeaderValue(deviceProfile.deviceId))
                put(
                    OpenSmsHeaders.DEVICE_NAME,
                    sanitizeHeaderValue(
                        DeviceDisplayName.sanitizeLabelSegment(deviceProfile.displayName)
                    )
                )
                put(OpenSmsHeaders.FINGERPRINT_VERSION, "2")
                put(OpenSmsHeaders.DEFAULT_REGION, deviceProfile.defaultRegion)
            }

            put(
                OpenSmsHeaders.ACCOUNT,
                sanitizeHeaderValue(
                    accountEmail
                )
            )
        }
    }

    private fun buildArchiveJson(
        conversation: SmsConversationSnapshot,
        accountEmail: String,
        snapshotHash: String,
        deviceProfile: DeviceProfile?
    ): String {

        val messages =
            JSONArray()

        conversation.messages.forEach { message ->

            messages.put(
                messageToJson(
                    message
                )
            )
        }

        val conversationJson =
            JSONObject().apply {

                put(
                    "threadId",
                    conversation.threadId
                )

                putNullable(
                    "address",
                    conversation.address
                )

                putNullable(
                    "contactName",
                    conversation.contactName
                )

                put(
                    "messageCount",
                    conversation.messageCount
                )

                put(
                    "firstMessageDate",
                    conversation.firstMessageDate
                )

                put(
                    "lastMessageDate",
                    conversation.lastMessageDate
                )

                put(
                    "messages",
                    messages
                )
            }

        return JSONObject().apply {

            put(
                "formatVersion",
                BACKUP_FORMAT_VERSION.toInt()
            )

            put(
                "backupType",
                "conversation-snapshot"
            )

            put(
                "account",
                accountEmail
            )

            put(
                "snapshotHash",
                snapshotHash
            )

            put(
                "createdAt",
                System.currentTimeMillis()
            )

            if (deviceProfile != null) {
                put("fingerprintVersion", 2)
                put("defaultRegion", deviceProfile.defaultRegion)
            }

            put(
                "conversation",
                conversationJson
            )

        }.toString()
    }

    private fun messageToJson(
        message: SmsMessage
    ): JSONObject {

        return JSONObject().apply {

            put(
                "id",
                message.id
            )

            put(
                "threadId",
                message.threadId
            )

            putNullable(
                "address",
                message.address
            )

            putNullable(
                "contactName",
                message.contactName
            )

            putNullable(
                "body",
                message.body
            )

            put(
                "date",
                message.date
            )

            put(
                "dateFormatted",
                message.dateFormatted
            )

            put(
                "type",
                message.type
            )

            put(
                "smsType",
                message.smsType.name
            )

            putNullable(
                "subscriptionId",
                message.subscriptionId
            )

            put(
                "isRead",
                message.isRead
            )

            putNullable(
                "serviceCenter",
                message.serviceCenter
            )
        }
    }

    private fun JSONObject.putNullable(
        name: String,
        value: Any?
    ) {

        put(
            name,
            value ?: JSONObject.NULL
        )
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

    private fun formatDateOnly(
        timestamp: Long
    ): String {

        return SimpleDateFormat(
            "dd MMM yyyy",
            Locale.getDefault()
        ).format(
            Date(timestamp)
        )
    }

    private fun messageBodyToHtml(
        value: String
    ): String {

        return escapeHtml(value)
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n", "<br>")
    }

    private fun escapeHtml(
        value: String
    ): String {

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun encodeDisplayName(
        value: String
    ): String {

        val sanitized =
            sanitizeHeaderValue(
                value
            )

        if (
            sanitized.all { character ->
                character.code in 32..126
            }
        ) {
            return "\"${escapeQuotedString(sanitized)}\""
        }

        val encoded =
            Base64.getEncoder()
                .encodeToString(
                    sanitized.toByteArray(
                        StandardCharsets.UTF_8
                    )
                )

        return "=?UTF-8?B?$encoded?="
    }

    private fun sanitizeEmailLocalPart(
        value: String,
        threadId: Long
    ): String {

        val normalized =
            Normalizer.normalize(
                value,
                Normalizer.Form.NFKD
            )

        val result =
            buildString {

                normalized.forEach { character ->

                    when {
                        character in 'a'..'z' ||
                                character in 'A'..'Z' ||
                                character in '0'..'9' -> {
                            append(character)
                        }

                        character == '.' ||
                                character == '-' ||
                                character == '_' -> {

                            if (
                                isNotEmpty() &&
                                last() != character
                            ) {
                                append(character)
                            }
                        }

                        character.isWhitespace() -> {

                            if (
                                isNotEmpty() &&
                                last() != '.'
                            ) {
                                append('.')
                            }
                        }

                        else -> {

                            if (
                                isNotEmpty() &&
                                last() != '-'
                            ) {
                                append('-')
                            }
                        }
                    }
                }
            }
                .trim('.', '-', '_')
                .replace(Regex("\\.{2,}"), ".")
                .replace(Regex("-{2,}"), "-")
                .replace(Regex("_{2,}"), "_")
                .take(MAX_LOCAL_PART_LENGTH)
                .trim('.', '-', '_')

        return result.ifBlank {
            "conversation-$threadId"
        }
    }

    private fun escapeQuotedString(
        value: String
    ): String {

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    private fun sanitizeHeaderValue(
        value: String
    ): String {

        return value
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
    }
}
