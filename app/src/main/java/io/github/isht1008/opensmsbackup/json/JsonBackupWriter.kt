package io.github.isht1008.opensmsbackup.json

import io.github.isht1008.opensmsbackup.backup.BackupResult
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class JsonBackupWriter {

    fun createJson(
        backup: BackupResult
    ): JSONObject {

        val root = JSONObject()

        root.put("formatVersion", 1)
        root.put("createdAt", Instant.now().toString())
        root.put("applicationName", "OpenSMS Backup")
        root.put("applicationVersion", "1.0")

        root.put("totalMessages", backup.totalMessages)
        root.put("totalConversations", backup.totalConversations)

        val conversationsArray = JSONArray()

        for (conversation in backup.conversations) {

            val conversationObject = JSONObject()

            conversationObject.put(
                "contactName",
                conversation.contactName
            )

            conversationObject.put(
                "phoneNumber",
                conversation.address
            )

            val messagesArray = JSONArray()

            for (message in conversation.messages) {

                val messageObject = JSONObject()

                messageObject.put("id", message.id)
                messageObject.put("body", message.body)
                messageObject.put("date", message.date)
                messageObject.put("type", message.type)

                messagesArray.put(messageObject)
            }

            conversationObject.put(
                "messages",
                messagesArray
            )

            conversationsArray.put(conversationObject)
        }

        root.put(
            "conversations",
            conversationsArray
        )

        return root
    }
}