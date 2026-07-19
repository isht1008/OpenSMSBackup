package io.github.isht1008.opensmsbackup.restore

import io.github.isht1008.opensmsbackup.gmail.backup.*
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import org.junit.Assert.*
import org.junit.Test

class RestoreCatalogTest {
    @Test fun `source device discovery accepts owned namespace and rejects unrelated account`() {
        val valid = document("one", "device-a", "12345", 10).copy(deviceNameHeader = "Old Phone")
        assertEquals(
            RestoreSourceDevice("device-a", "Old Phone", "label-a"),
            RestoreCatalog.sourceDevice(valid, "USER@example.com", "label-a", "Fallback")
        )
        assertNull(RestoreCatalog.sourceDevice(valid, "other@example.com", "label-a", "Fallback"))
    }

    @Test fun `newest valid snapshot per conversation is selected and unrelated ignored`() {
        val old = document("old", "device-a", "12345", 10)
        val newest = document("new", "device-a", "12345", 20)
        val otherDevice = document("other", "device-b", "12345", 30)
        val malformed = old.copy(messageId = "bad", conversationKeyHeader = "wrong", internalDate = 40)
        val result = RestoreCatalog.newestValid(listOf(old, newest, otherDevice, malformed),
            "user@example.com", "device-a", "label-a")
        assertEquals(listOf("new"), result.map { if (it.internalDate == 20L) "new" else "old" })
    }

    @Test fun `preview counts are accurate`() {
        val device = RestoreSourceDevice("device-a", "Phone", "label-a")
        val messages = listOf(message(1, 1), message(2, 2), message(3, 1))
        val preview = RestorePreview("p", "user@example.com", device,
            listOf(RestoreConversation("k", SmsConversationSnapshot(1, "HDFCBK", null, messages), 3)), 1)
        assertEquals(3, preview.messageCount); assertEquals(2, preview.receivedCount)
        assertEquals(1, preview.sentCount); assertEquals(1L, preview.earliestDate); assertEquals(3L, preview.latestDate)
    }

    private fun document(id: String, device: String, address: String, date: Long): GmailArchiveDocument {
        val conversation = SmsConversationSnapshot(1, address, null, listOf(message(date, 1)))
        val key = ArchiveConversationIdentity.key(
            ArchiveConversationIdentity.Version.V2_ACCOUNT_DEVICE_ADDRESS,
            address, "user@example.com", device
        )
        return GmailArchiveDocument(id, null, date, "user@example.com", key, conversation,
            device, "2", setOf("label-a"))
    }
    private fun message(id: Long, type: Int) = SmsMessage(id, address = "HDFCBK", contactName = null,
        body = "body", date = id, dateFormatted = "", type = type)
}
