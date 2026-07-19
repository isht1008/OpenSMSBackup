package io.github.isht1008.opensmsbackup.restore

import android.content.Context
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RestorePlan(
    val profileId: String,
    val accountEmail: String,
    val sourceDeviceName: String,
    val defaultRegion: String,
    val messages: List<SmsMessage>
)

class RestorePlanStore(context: Context) {
    private val directory = File(context.filesDir, "restore-plans").apply { mkdirs() }

    fun write(id: String, plan: RestorePlan) {
        val messages = JSONArray().apply { plan.messages.forEach { put(messageJson(it)) } }
        val root = JSONObject().put("profileId", plan.profileId)
            .put("accountEmail", plan.accountEmail).put("sourceDeviceName", plan.sourceDeviceName)
            .put("defaultRegion", plan.defaultRegion).put("messages", messages)
        file(id).writeText(root.toString())
    }

    fun read(id: String): RestorePlan? = runCatching {
        val root = JSONObject(file(id).readText())
        val array = root.getJSONArray("messages")
        val messages = ArrayList<SmsMessage>(array.length())
        for (i in 0 until array.length()) messages += parseMessage(array.getJSONObject(i))
        RestorePlan(root.getString("profileId"), root.getString("accountEmail"),
            root.getString("sourceDeviceName"), root.getString("defaultRegion"), messages)
    }.getOrNull()

    fun delete(id: String) { file(id).delete() }
    private fun file(id: String) = File(directory, "$id.json")

    private fun messageJson(m: SmsMessage) = JSONObject().put("id", m.id)
        .apply { if (m.address == null) put("address", JSONObject.NULL) else put("address", m.address) }
        .apply { if (m.body == null) put("body", JSONObject.NULL) else put("body", m.body) }
        .put("date", m.date).put("type", m.type).put("read", m.isRead)

    private fun parseMessage(o: JSONObject) = SmsMessage(
        o.getLong("id"), address = o.takeUnless { it.isNull("address") }?.optString("address")?.takeIf { it.isNotEmpty() },
        contactName = null, body = o.takeUnless { it.isNull("body") }?.optString("body")?.takeIf { it.isNotEmpty() },
        date = o.getLong("date"), dateFormatted = "", type = o.getInt("type"),
        isRead = o.optBoolean("read", true)
    )
}
