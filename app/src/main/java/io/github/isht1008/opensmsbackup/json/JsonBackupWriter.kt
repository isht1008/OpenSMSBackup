package io.github.isht1008.opensmsbackup.json

import io.github.isht1008.opensmsbackup.backup.BackupResult
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import io.github.isht1008.opensmsbackup.util.SmsTypeUtils


class JsonBackupWriter {

    fun createJson(
        backup: BackupResult
    ): JSONObject {

        val root = JSONObject()

        root.put("formatVersion", 2)
        root.put("createdAt", Instant.now().toString())

        root.put(
            "backupTimeZone",
            java.util.TimeZone.getDefault().id
        )

        root.put(
            "backupLocale",
            java.util.Locale.getDefault().toLanguageTag()
        )

        val application = JSONObject()

        application.put("name", "OpenSMS Backup")
        application.put("version", "1.0")

        root.put("application", application)

        val device = JSONObject()

        device.put("manufacturer", android.os.Build.MANUFACTURER)
        device.put("brand", android.os.Build.BRAND)
        device.put("model", android.os.Build.MODEL)
        device.put("androidVersion", android.os.Build.VERSION.RELEASE)
        device.put("sdkInt", android.os.Build.VERSION.SDK_INT)

        root.put("device", device)

        val statistics = JSONObject()

        statistics.put("totalMessages", backup.totalMessages)
        statistics.put("totalConversations", backup.totalConversations)

        root.put("statistics", statistics)

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
                messageObject.put("dateFormatted", message.dateFormatted)

                messageObject.put(
                    "type",
                    SmsTypeUtils.getTypeName(message.type)
                )

                messageObject.put(
                    "typeCode",
                    message.type
                )

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