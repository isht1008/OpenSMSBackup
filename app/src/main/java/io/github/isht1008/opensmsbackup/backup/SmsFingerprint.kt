package io.github.isht1008.opensmsbackup.backup

import java.security.MessageDigest

object SmsFingerprint {

    fun create(
        address: String?,
        body: String?,
        date: Long,
        type: Int
    ): String {

        val input =
            buildString {

                append(address ?: "")
                append('|')

                append(body ?: "")
                append('|')

                append(date)
                append('|')

                append(type)

            }

        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray())

        return digest.joinToString("") {
            "%02x".format(it)
        }
    }
}