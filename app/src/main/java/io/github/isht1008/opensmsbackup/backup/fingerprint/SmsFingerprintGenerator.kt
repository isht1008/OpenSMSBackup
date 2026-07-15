package io.github.isht1008.opensmsbackup.backup.fingerprint

import java.security.MessageDigest

object SmsFingerprintGenerator {

    fun generate(
        address: String?,
        body: String?,
        date: Long,
        type: Int
    ): String {

        val input = buildString {
            append(address ?: "")
            append('|')
            append(date)
            append('|')
            append(type)
            append('|')
            append(body ?: "")
        }

        return sha256(input)
    }

    private fun sha256(
        value: String
    ): String {

        val bytes =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray())

        return bytes.joinToString("") {
            "%02x".format(it)
        }

    }

}