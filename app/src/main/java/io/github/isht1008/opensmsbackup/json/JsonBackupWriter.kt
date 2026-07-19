package io.github.isht1008.opensmsbackup.json

import android.util.JsonWriter
import io.github.isht1008.opensmsbackup.backup.BackupResult
import java.time.Instant
import io.github.isht1008.opensmsbackup.util.SmsTypeUtils


class JsonBackupWriter {

    fun writeJson(writer: JsonWriter, backup: BackupResult) {
        writer.beginObject()
        writer.name("formatVersion").value(2)
        writer.name("createdAt").value(Instant.now().toString())
        writer.name("backupTimeZone").value(java.util.TimeZone.getDefault().id)
        writer.name("backupLocale").value(java.util.Locale.getDefault().toLanguageTag())
        writer.name("application").beginObject()
        writer.name("name").value("OpenSMS Backup")
        writer.name("version").value("1.0")
        writer.endObject()
        writer.name("device").beginObject()
        writer.name("manufacturer").value(android.os.Build.MANUFACTURER)
        writer.name("brand").value(android.os.Build.BRAND)
        writer.name("model").value(android.os.Build.MODEL)
        writer.name("androidVersion").value(android.os.Build.VERSION.RELEASE)
        writer.name("sdkInt").value(android.os.Build.VERSION.SDK_INT.toLong())
        writer.endObject()
        writer.name("statistics").beginObject()
        writer.name("totalMessages").value(backup.totalMessages.toLong())
        writer.name("totalConversations").value(backup.totalConversations.toLong())
        writer.endObject()
        writer.name("conversations").beginArray()
        backup.conversations.forEach { conversation ->
            writer.beginObject()
            conversation.contactName?.let { writer.name("contactName").value(it) }
            writer.name("phoneNumber").value(conversation.address)
            writer.name("messages").beginArray()
            conversation.messages.forEach { message ->
                writer.beginObject()
                writer.name("id").value(message.id)
                message.body?.let { writer.name("body").value(it) }
                writer.name("date").value(message.date)
                writer.name("dateFormatted").value(message.dateFormatted)
                writer.name("type").value(SmsTypeUtils.getTypeName(message.type))
                writer.name("typeCode").value(message.type.toLong())
                writer.endObject()
            }
            writer.endArray()
            writer.endObject()
        }
        writer.endArray()
        writer.endObject()
    }
}
