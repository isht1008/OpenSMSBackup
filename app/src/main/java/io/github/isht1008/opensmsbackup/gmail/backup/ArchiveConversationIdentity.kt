package io.github.isht1008.opensmsbackup.gmail.backup

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object ArchiveConversationIdentity {
    fun key(address: String?, accountEmail: String): String {
        val normalizedAddress = SmsFingerprint.normalizeAddress(address)
            .ifBlank { "<unknown-address>" }
        val normalizedAccount = accountEmail.trim().lowercase(Locale.ROOT)
        require(normalizedAccount.isNotBlank()) {
            "Gmail account email is required for archive discovery."
        }
        val input = "archive-v1\u0000$normalizedAccount\u0000$normalizedAddress"
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
