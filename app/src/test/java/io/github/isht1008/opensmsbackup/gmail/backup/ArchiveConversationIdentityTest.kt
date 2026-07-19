package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.gmail.header.OpenSmsHeaders
import io.github.isht1008.opensmsbackup.gmail.mime.ConversationMimeMessageBuilder
import io.github.isht1008.opensmsbackup.device.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArchiveConversationIdentityTest {
    @Test fun `conversation key is deterministic across formatting and account case`() {
        assertEquals(
            ArchiveConversationIdentity.key("+1 (555) 123-4567", "User@Example.com"),
            ArchiveConversationIdentity.key("001-555-123-4567", "user@example.com")
        )
    }

    @Test fun `conversation key separates addresses and accounts`() {
        val key = ArchiveConversationIdentity.key("12345", "one@example.com")
        assertNotEquals(key, ArchiveConversationIdentity.key("54321", "one@example.com"))
        assertNotEquals(key, ArchiveConversationIdentity.key("12345", "two@example.com"))
    }

    @Test fun `V2 identity is stable for same device and isolated between devices`() {
        fun key(device: String) = ArchiveConversationIdentity.key(
            ArchiveConversationIdentity.Version.V2_ACCOUNT_DEVICE_ADDRESS,
            "+1 (555) 123-4567",
            "User@Example.com",
            device
        )
        assertEquals(key("device-a"), key("device-a"))
        assertNotEquals(key("device-a"), key("device-b"))
    }

    @Test fun `V3 country identity canonicalizes equivalent numbers and isolates devices`() {
        fun key(address: String, device: String) = ArchiveConversationIdentity.key(
            ArchiveConversationIdentity.Version.V3_ACCOUNT_DEVICE_COUNTRY_ADDRESS,
            address,
            "User@Example.com",
            device,
            "IN"
        )

        assertEquals(key("09876543210", "device-a"), key("+91 98765 43210", "device-a"))
        assertNotEquals(key("09876543210", "device-a"), key("09876543210", "device-b"))
    }

    @Test fun `format three email includes backward compatible conversation key header`() {
        val conversation = SmsConversationSnapshot(7, "12345", null, emptyList())
        val email = ConversationMimeMessageBuilder().build(
            conversation,
            "user@example.com",
            "hash"
        )
        assertEquals(
            ArchiveConversationIdentity.key("12345", "user@example.com"),
            email.headers[OpenSmsHeaders.CONVERSATION_KEY]
        )
    }

    @Test fun `new device snapshot declares country aware identity and fingerprint metadata`() {
        val profile = DeviceProfile(
            "device-a", "Samsung", "SM-G990B", null, "15", null, null,
            "Test Phone", 1, 1, "IN"
        )
        val conversation = SmsConversationSnapshot(7, "09876543210", null, emptyList())
        val email = ConversationMimeMessageBuilder().build(
            conversation, "user@example.com", "hash", profile
        )

        assertEquals("3", email.headers[OpenSmsHeaders.ARCHIVE_IDENTITY_VERSION])
        assertEquals("2", email.headers[OpenSmsHeaders.FINGERPRINT_VERSION])
        assertEquals("IN", email.headers[OpenSmsHeaders.DEFAULT_REGION])
        assertEquals(
            ArchiveConversationIdentity.key(
                ArchiveConversationIdentity.Version.V3_ACCOUNT_DEVICE_COUNTRY_ADDRESS,
                conversation.address,
                "user@example.com",
                "device-a",
                "IN"
            ),
            email.headers[OpenSmsHeaders.CONVERSATION_KEY]
        )
    }
}
