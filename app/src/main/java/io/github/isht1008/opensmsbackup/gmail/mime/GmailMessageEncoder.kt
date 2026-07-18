package io.github.isht1008.opensmsbackup.gmail.mime

import io.github.isht1008.opensmsbackup.gmail.model.SmsEmail
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

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
        }
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

}