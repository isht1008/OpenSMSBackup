package io.github.isht1008.opensmsbackup.gmail.backup

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object ArchiveConversationIdentity {
    enum class Version(val wireName: String) {
        V1_ACCOUNT_ADDRESS("archive-v1"),
        V2_ACCOUNT_DEVICE_ADDRESS("archive-v2")
    }

    fun key(address: String?, accountEmail: String): String =
        key(Version.V1_ACCOUNT_ADDRESS, address, accountEmail, null)

    fun key(
        version: Version,
        address: String?,
        accountEmail: String,
        deviceId: String?
    ): String {
        val normalizedAddress = SmsFingerprint.normalizeAddress(address)
            .ifBlank { "<unknown-address>" }
        val normalizedAccount = accountEmail.trim().lowercase(Locale.ROOT)
        require(normalizedAccount.isNotBlank()) {
            "Gmail account email is required for archive discovery."
        }
        val normalizedDevice = when (version) {
            Version.V1_ACCOUNT_ADDRESS -> ""
            Version.V2_ACCOUNT_DEVICE_ADDRESS -> requireNotNull(deviceId)
                .trim().lowercase(Locale.ROOT).also { require(it.isNotBlank()) }
        }
        val input = when (version) {
            Version.V1_ACCOUNT_ADDRESS ->
                "${version.wireName}\u0000$normalizedAccount\u0000$normalizedAddress"
            Version.V2_ACCOUNT_DEVICE_ADDRESS ->
                "${version.wireName}\u0000$normalizedAccount\u0000$normalizedDevice\u0000$normalizedAddress"
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
