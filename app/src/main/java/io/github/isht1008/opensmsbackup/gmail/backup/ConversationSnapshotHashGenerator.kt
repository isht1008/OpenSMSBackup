package io.github.isht1008.opensmsbackup.gmail.backup

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object ConversationSnapshotHashGenerator {

    /*
     * Increment this value whenever the visible Gmail snapshot format changes.
     * It forces existing conversations to be regenerated once with the new
     * layout, while preserving normal incremental backup behaviour afterward.
     */
    private const val SNAPSHOT_RENDER_VERSION =
        "2"

    fun generate(
        conversation: SmsConversationSnapshot
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        fun add(value: Any?) {

            digest.update(
                (value?.toString() ?: "<null>")
                    .toByteArray(
                        StandardCharsets.UTF_8
                    )
            )

            digest.update(
                0.toByte()
            )
        }

        add(
            SNAPSHOT_RENDER_VERSION
        )

        add(
            conversation.threadId
        )

        add(
            conversation.address
        )

        add(
            conversation.contactName
        )

        add(
            conversation.messageCount
        )

        conversation.messages.forEach { message ->

            add(
                message.id
            )

            add(
                message.threadId
            )

            add(
                message.address
            )

            add(
                message.contactName
            )

            add(
                message.body
            )

            add(
                message.date
            )

            add(
                message.type
            )

            add(
                message.subscriptionId
            )

            add(
                message.isRead
            )

            add(
                message.serviceCenter
            )
        }

        return digest.digest()
            .joinToString("") { byte ->

                "%02x".format(
                    byte.toInt() and 0xff
                )
            }
    }
}
