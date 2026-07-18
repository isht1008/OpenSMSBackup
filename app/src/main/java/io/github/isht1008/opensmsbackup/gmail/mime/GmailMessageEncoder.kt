package io.github.isht1008.opensmsbackup.gmail.mime

import io.github.isht1008.opensmsbackup.gmail.model.EmailAttachment
import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID

class GmailMessageEncoder {

    fun encode(
        email: SmsEmail
    ): String {

        val rawMessage =
            buildRawMessage(
                email = email
            )

        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                rawMessage.toByteArray(
                    StandardCharsets.UTF_8
                )
            )
    }

    private fun buildRawMessage(
        email: SmsEmail
    ): String {

        return buildString {

            appendCommonHeaders(
                email
            )

            when {
                email.attachments.isNotEmpty() -> {
                    appendMultipartMixedBody(
                        email = email
                    )
                }

                !email.htmlBody.isNullOrBlank() -> {
                    appendMultipartAlternativeBody(
                        plainBody = email.body,
                        htmlBody = email.htmlBody
                    )
                }

                else -> {
                    appendPlainTextBody(
                        email.body
                    )
                }
            }
        }
    }

    private fun StringBuilder.appendCommonHeaders(
        email: SmsEmail
    ) {

        appendHeader(
            name = "From",
            value = email.from
        )

        appendHeader(
            name = "To",
            value = email.to
        )

        appendHeader(
            name = "Subject",
            value = encodeHeaderValue(
                email.subject
            )
        )

        appendHeader(
            name = "Date",
            value = formatEmailDate(
                email.date
            )
        )

        email.headers.forEach {
                (name, value) ->

            appendHeader(
                name = sanitizeHeaderName(
                    name
                ),
                value = sanitizeHeaderValue(
                    value
                )
            )
        }

        appendHeader(
            name = "MIME-Version",
            value = "1.0"
        )
    }

    private fun StringBuilder.appendPlainTextBody(
        body: String
    ) {

        appendHeader(
            name = "Content-Type",
            value = "text/plain; charset=UTF-8"
        )

        appendHeader(
            name = "Content-Transfer-Encoding",
            value = "8bit"
        )

        append("\r\n")
        append(
            normalizeBodyLineEndings(
                body
            )
        )
    }

    private fun StringBuilder.appendMultipartAlternativeBody(
        plainBody: String,
        htmlBody: String
    ) {

        val boundary =
            "OpenSMSBackup_Alternative_${UUID.randomUUID()}"

        appendHeader(
            name = "Content-Type",
            value =
                "multipart/alternative; boundary=\"$boundary\""
        )

        append("\r\n")

        appendAlternativeParts(
            boundary = boundary,
            plainBody = plainBody,
            htmlBody = htmlBody
        )
    }

    private fun StringBuilder.appendMultipartMixedBody(
        email: SmsEmail
    ) {

        val mixedBoundary =
            "OpenSMSBackup_Mixed_${UUID.randomUUID()}"

        appendHeader(
            name = "Content-Type",
            value =
                "multipart/mixed; boundary=\"$mixedBoundary\""
        )

        append("\r\n")

        append("--$mixedBoundary\r\n")

        val htmlBody =
            email.htmlBody

        if (htmlBody.isNullOrBlank()) {

            appendHeader(
                name = "Content-Type",
                value = "text/plain; charset=UTF-8"
            )

            appendHeader(
                name = "Content-Transfer-Encoding",
                value = "8bit"
            )

            append("\r\n")

            append(
                normalizeBodyLineEndings(
                    email.body
                )
            )

            append("\r\n")

        } else {

            val alternativeBoundary =
                "OpenSMSBackup_Alternative_${UUID.randomUUID()}"

            appendHeader(
                name = "Content-Type",
                value =
                    "multipart/alternative; boundary=\"$alternativeBoundary\""
            )

            append("\r\n")

            appendAlternativeParts(
                boundary = alternativeBoundary,
                plainBody = email.body,
                htmlBody = htmlBody
            )
        }

        email.attachments.forEach { attachment ->

            appendAttachment(
                boundary = mixedBoundary,
                attachment = attachment
            )
        }

        append("--$mixedBoundary--\r\n")
    }

    private fun StringBuilder.appendAlternativeParts(
        boundary: String,
        plainBody: String,
        htmlBody: String
    ) {

        append("--$boundary\r\n")

        appendHeader(
            name = "Content-Type",
            value = "text/plain; charset=UTF-8"
        )

        appendHeader(
            name = "Content-Transfer-Encoding",
            value = "8bit"
        )

        append("\r\n")

        append(
            normalizeBodyLineEndings(
                plainBody
            )
        )

        append("\r\n")

        append("--$boundary\r\n")

        appendHeader(
            name = "Content-Type",
            value = "text/html; charset=UTF-8"
        )

        appendHeader(
            name = "Content-Transfer-Encoding",
            value = "8bit"
        )

        append("\r\n")

        append(
            normalizeBodyLineEndings(
                htmlBody
            )
        )

        append("\r\n")
        append("--$boundary--\r\n")
    }

    private fun StringBuilder.appendAttachment(
        boundary: String,
        attachment: EmailAttachment
    ) {

        val fileName =
            sanitizeFileName(
                attachment.fileName
            )

        append("--$boundary\r\n")

        appendHeader(
            name = "Content-Type",
            value =
                "${sanitizeMimeType(attachment.mimeType)}; name=\"$fileName\""
        )

        appendHeader(
            name = "Content-Disposition",
            value =
                "attachment; filename=\"$fileName\""
        )

        appendHeader(
            name = "Content-Transfer-Encoding",
            value = "base64"
        )

        append("\r\n")

        append(
            Base64.getMimeEncoder(
                76,
                "\r\n".toByteArray(
                    StandardCharsets.US_ASCII
                )
            ).encodeToString(
                attachment.content
            )
        )

        append("\r\n")
    }

    private fun StringBuilder.appendHeader(
        name: String,
        value: String
    ) {

        append(name)
        append(": ")
        append(value)
        append("\r\n")
    }

    private fun encodeHeaderValue(
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
            return sanitized
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

    private fun formatEmailDate(
        timestamp: Long
    ): String {

        val formatter =
            SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss Z",
                Locale.US
            )

        return formatter.format(
            Date(timestamp)
        )
    }

    private fun normalizeBodyLineEndings(
        body: String
    ): String {

        return body
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n", "\r\n")
    }

    private fun sanitizeHeaderName(
        value: String
    ): String {

        return value
            .filter { character ->
                character.isLetterOrDigit() ||
                        character == '-'
            }
            .ifBlank {
                "X-OpenSMSBackup-Header"
            }
    }

    private fun sanitizeHeaderValue(
        value: String
    ): String {

        return value
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
    }

    private fun sanitizeFileName(
        value: String
    ): String {

        return value
            .replace("\r", "_")
            .replace("\n", "_")
            .replace("\"", "_")
            .replace("\\", "_")
            .ifBlank {
                "opensmsbackup-attachment.dat"
            }
    }

    private fun sanitizeMimeType(
        value: String
    ): String {

        return value
            .filter { character ->
                character.isLetterOrDigit() ||
                        character == '/' ||
                        character == '-' ||
                        character == '.' ||
                        character == '+'
            }
            .ifBlank {
                "application/octet-stream"
            }
    }
}
