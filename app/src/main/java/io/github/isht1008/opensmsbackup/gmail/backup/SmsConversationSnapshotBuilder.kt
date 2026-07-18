package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.sms.SmsMessage

class SmsConversationSnapshotBuilder {

    fun build(
        messages: List<SmsMessage>
    ): List<SmsConversationSnapshot> {

        return messages
            .groupBy { message ->
                message.threadId
            }
            .map { (threadId, threadMessages) ->

                val sortedMessages =
                    threadMessages.sortedWith(
                        compareBy(
                            { message -> message.date },
                            { message -> message.id }
                        )
                    )

                SmsConversationSnapshot(
                    threadId = threadId,
                    address = findLatestAddress(
                        sortedMessages
                    ),
                    contactName = findLatestContactName(
                        sortedMessages
                    ),
                    messages = sortedMessages
                )
            }
            .sortedByDescending { conversation ->
                conversation.lastMessageDate
            }
    }

    private fun findLatestAddress(
        messages: List<SmsMessage>
    ): String? {

        return messages
            .asReversed()
            .firstNotNullOfOrNull { message ->
                message.address
                    ?.trim()
                    ?.takeIf { address ->
                        address.isNotEmpty()
                    }
            }
    }

    private fun findLatestContactName(
        messages: List<SmsMessage>
    ): String? {

        return messages
            .asReversed()
            .firstNotNullOfOrNull { message ->
                message.contactName
                    ?.trim()
                    ?.takeIf { contactName ->
                        contactName.isNotEmpty()
                    }
            }
    }
}
