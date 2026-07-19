package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.sms.SmsMessage
import io.github.isht1008.opensmsbackup.sms.SmsType
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object SmsFingerprint {
    fun generate(message: SmsMessage): String {
        val digest = MessageDigest.getInstance("SHA-256")

        listOf(
            normalizeAddress(message.address),
            direction(message.smsType),
            message.date.toString(),
            message.body.orEmpty()
        ).forEach { value ->
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(StandardCharsets.UTF_8))
            digest.update(':'.code.toByte())
            digest.update(bytes)
        }

        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    fun normalizeAddress(address: String?): String {
        val trimmed = address.orEmpty().trim()
        val digits = trimmed.filter(Char::isDigit)
        if (digits.isNotEmpty()) {
            return digits.removePrefix("00")
        }
        return trimmed.lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }
    }

    private fun direction(type: SmsType): String =
        if (type == SmsType.SENT) "sent" else "received"
}
