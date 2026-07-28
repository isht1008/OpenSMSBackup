package io.github.isht1008.opensmsbackup.gmail.backup

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object LocalConversationSourceHashGenerator {
    // Rendered dates are deliberately excluded: they are derived from the raw timestamp and
    // depend on the process Locale/TimeZone. Renderer changes must bump this explicit version.
    private const val CHECKPOINT_RENDER_VERSION = "local-source-v2"

    fun generate(conversation: SmsConversationSnapshot): String =
        generate(
            conversation = conversation,
            version = CHECKPOINT_RENDER_VERSION,
            includeFormattedDate = false
        )

    internal fun generateLegacyV1(conversation: SmsConversationSnapshot): String =
        generate(
            conversation = conversation,
            version = "local-source-v1",
            includeFormattedDate = true
        )

    private fun generate(
        conversation: SmsConversationSnapshot,
        version: String,
        includeFormattedDate: Boolean
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(value: Any?) {
            digest.update(
                (value?.toString() ?: "<null>").toByteArray(StandardCharsets.UTF_8)
            )
            digest.update(0)
        }

        add(version)
        add(conversation.threadId)
        add(conversation.address)
        add(conversation.contactName)
        add(conversation.messageCount)
        val messages =
            if (includeFormattedDate) {
                conversation.messages
            } else {
                conversation.messages.sortedWith(
                    compareBy(
                        { it.date },
                        { it.id },
                        { it.threadId },
                        { it.type },
                        { it.address.orEmpty() },
                        { it.body.orEmpty() }
                    )
                )
            }
        messages.forEach { message ->
            add(message.id)
            add(message.threadId)
            add(message.address)
            add(message.contactName)
            add(message.body)
            add(message.date)
            if (includeFormattedDate) add(message.dateFormatted)
            add(message.type)
            add(message.subscriptionId)
            add(message.isRead)
            add(message.serviceCenter)
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
