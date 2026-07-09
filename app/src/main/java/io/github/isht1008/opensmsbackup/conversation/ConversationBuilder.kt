package io.github.isht1008.opensmsbackup.conversation

import io.github.isht1008.opensmsbackup.model.Conversation
import io.github.isht1008.opensmsbackup.sms.SmsMessage

class ConversationBuilder {

    fun build(
        messages: List<SmsMessage>
    ): List<Conversation> {

        val conversations =
            linkedMapOf<String, Conversation>()

        for (message in messages) {

            val address = message.address ?: continue

            val conversation =
                conversations.getOrPut(address) {

                    Conversation(
                        address = address,
                        contactName = message.contactName
                    )

                }

            conversation.messages.add(message)
        }

        return conversations.values.toList()
    }
}