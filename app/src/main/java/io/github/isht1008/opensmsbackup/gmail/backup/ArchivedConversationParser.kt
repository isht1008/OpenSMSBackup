package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.sms.SmsMessage
import org.json.JSONObject

class ArchivedConversationParser {
    data class ParsedArchive(
        val accountEmail: String,
        val conversation: SmsConversationSnapshot
    )

    fun parse(json: String): SmsConversationSnapshot =
        parseArchive(json).conversation

    fun parseArchive(json: String): ParsedArchive {
        val root = JSONObject(json)
        require(root.getInt("formatVersion") == 3) {
            "Unsupported Gmail conversation snapshot format."
        }
        val conversation = root.getJSONObject("conversation")
        val messagesJson = conversation.getJSONArray("messages")
        val messages = ArrayList<SmsMessage>(messagesJson.length())

        for (index in 0 until messagesJson.length()) {
            val message = messagesJson.getJSONObject(index)
            messages += SmsMessage(
                id = message.getLong("id"),
                threadId = message.getLong("threadId"),
                address = message.optionalString("address"),
                contactName = message.optionalString("contactName"),
                body = message.optionalString("body"),
                date = message.getLong("date"),
                dateFormatted = message.optString("dateFormatted"),
                type = message.getInt("type"),
                subscriptionId = message.optionalInt("subscriptionId"),
                isRead = message.optBoolean("isRead", true),
                serviceCenter = message.optionalString("serviceCenter")
            )
        }

        return ParsedArchive(
            accountEmail = root.getString("account"),
            conversation = SmsConversationSnapshot(
                threadId = conversation.getLong("threadId"),
                address = conversation.optionalString("address"),
                contactName = conversation.optionalString("contactName"),
                messages = messages
            )
        )
    }

    private fun JSONObject.optionalString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

    private fun JSONObject.optionalInt(name: String): Int? =
        if (isNull(name) || !has(name)) null else getInt(name)
}
