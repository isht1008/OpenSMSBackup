package io.github.isht1008.opensmsbackup.gmail.mirror

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object MirrorConversationIdentity {
    const val WIRE_NAME = "mirror-thread-v1"

    fun key(profileId: String, accountIdentity: String, deviceId: String, androidThreadId: Long): String {
        require(profileId.isNotBlank())
        require(accountIdentity.isNotBlank())
        require(deviceId.isNotBlank())
        require(androidThreadId > 0L)
        val material = listOf(
            WIRE_NAME,
            profileId.trim(),
            accountIdentity.trim().lowercase(Locale.ROOT),
            deviceId.trim(),
            androidThreadId.toString()
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}