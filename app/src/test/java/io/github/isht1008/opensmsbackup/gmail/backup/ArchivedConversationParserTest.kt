package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.gmail.mime.ConversationMimeMessageBuilder
import io.github.isht1008.opensmsbackup.sms.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class ArchivedConversationParserTest {
    @Test fun `parser reads the existing format three restore attachment`() {
        val original = SmsConversationSnapshot(
            threadId = 7,
            address = "+1 555 123 4567",
            contactName = "Contact",
            messages = listOf(
                SmsMessage(1, 7, "+1 555 123 4567", "Contact", "hello", 100, "date", 1)
            )
        )
        val attachment = ConversationMimeMessageBuilder()
            .build(original, "user@example.com", "hash")
            .attachments.single()

        val parsed = ArchivedConversationParser().parse(
            String(attachment.content, StandardCharsets.UTF_8)
        )

        assertEquals(original, parsed)
    }
}
