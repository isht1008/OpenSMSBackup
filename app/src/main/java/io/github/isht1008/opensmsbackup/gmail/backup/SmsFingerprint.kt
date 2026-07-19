package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.sms.SmsAddressNormalizer
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import io.github.isht1008.opensmsbackup.sms.SmsType
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

enum class SmsFingerprintVersion {
    V1_LEGACY,
    V2_COUNTRY_AWARE
}

object SmsFingerprint {
    private val addressNormalizer = SmsAddressNormalizer()

    fun generate(message: SmsMessage): String = generateV1(message)

    fun generate(
        message: SmsMessage,
        version: SmsFingerprintVersion,
        defaultRegion: String
    ): String = when (version) {
        SmsFingerprintVersion.V1_LEGACY -> generateV1(message)
        SmsFingerprintVersion.V2_COUNTRY_AWARE -> digest(
            listOf(
                "fingerprint-v2",
                addressNormalizer.normalize(message.address, defaultRegion).canonical,
                direction(message.smsType),
                message.date.toString(),
                message.body.orEmpty()
            )
        )
    }

    fun aliases(message: SmsMessage, defaultRegion: String): Set<String> = setOf(
        generateV1(message),
        generate(message, SmsFingerprintVersion.V2_COUNTRY_AWARE, defaultRegion)
    )

    fun normalizeAddress(address: String?): String {
        val trimmed = address.orEmpty().trim()
        val digits = trimmed.filter(Char::isDigit)
        if (digits.isNotEmpty()) return digits.removePrefix("00")
        return trimmed.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    }

    private fun generateV1(message: SmsMessage): String = digest(
        listOf(
            normalizeAddress(message.address),
            direction(message.smsType),
            message.date.toString(),
            message.body.orEmpty()
        )
    )

    private fun digest(values: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach { value ->
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(StandardCharsets.UTF_8))
            digest.update(':'.code.toByte())
            digest.update(bytes)
        }
        val bytes = digest.digest()
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
    }

    private fun direction(type: SmsType): String =
        if (type == SmsType.SENT) "sent" else "received"

    private const val HEX = "0123456789abcdef"
}
