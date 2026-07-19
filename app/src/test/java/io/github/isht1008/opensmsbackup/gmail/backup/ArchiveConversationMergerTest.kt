package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.sms.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class ArchiveConversationMergerTest {
    private val merger = ArchiveConversationMerger()

    @Test fun `empty archive contains unique phone messages`() {
        val phone = conversation(message(1, 100), message(2, 200))
        val result = merger.merge(null, phone)
        assertEquals(2, result.appendedCount)
        assertEquals(listOf(1L, 2L), result.conversation.messages.map { it.id })
    }

    @Test fun `existing archive appends only new phone messages`() {
        val archived = conversation(message(1, 100))
        val phone = conversation(message(9, 100), message(2, 200))
        val result = merger.merge(archived, phone)
        assertEquals(1, result.appendedCount)
        assertEquals(listOf(1L, 2L), result.conversation.messages.map { it.id })
    }

    @Test fun `deleted phone messages remain archived`() {
        val archived = conversation(message(1, 100), message(2, 200))
        val phone = conversation(message(9, 200))
        val result = merger.merge(archived, phone)
        assertEquals(0, result.appendedCount)
        assertEquals(listOf(1L, 2L), result.conversation.messages.map { it.id })
    }

    @Test fun `duplicate phone messages are ignored`() {
        val duplicate = message(1, 100)
        val result = merger.merge(null, conversation(duplicate, duplicate.copy(id = 2)))
        assertEquals(1, result.appendedCount)
        assertEquals(1, result.conversation.messageCount)
    }

    @Test fun `merge preserves chronological ordering`() {
        val archived = conversation(message(1, 300), message(2, 100))
        val phone = conversation(message(3, 200))
        val result = merger.merge(archived, phone)
        assertEquals(listOf(100L, 200L, 300L), result.conversation.messages.map { it.date })
    }

    @Test fun `legacy archive national format deduplicates current international format`() {
        val countryMerger = ArchiveConversationMerger("IN")
        val archivedMessage = SmsMessage(1, 7, "09876543210", null, "same", 100, "", 1)
        val phoneMessage = archivedMessage.copy(id = 2, address = "+91 98765 43210")

        val result = countryMerger.merge(
            SmsConversationSnapshot(7, "09876543210", null, listOf(archivedMessage)),
            SmsConversationSnapshot(7, "+91 98765 43210", null, listOf(phoneMessage))
        )

        assertEquals(0, result.appendedCount)
        assertEquals(1, result.conversation.messageCount)
    }

    @Test fun `large conversation merge remains linear enough for practical use`() {
        val archived = conversation(*Array(10_000) { message(it.toLong(), it.toLong()) })
        val phone = conversation(*Array(10_000) {
            message((it + 5_000).toLong(), (it + 5_000).toLong())
        })

        val elapsed = measureTimeMillis {
            val result = merger.merge(archived, phone)
            assertEquals(5_000, result.appendedCount)
            assertEquals(15_000, result.conversation.messageCount)
        }
        assertTrue("Merge took ${elapsed}ms", elapsed < 5_000)
    }

    private fun conversation(vararg messages: SmsMessage) =
        SmsConversationSnapshot(7, "15551234567", null, messages.toList())

    private fun message(id: Long, date: Long) =
        SmsMessage(id, 7, "+1 555 123 4567", null, "body-$date", date, "", 1)
}
