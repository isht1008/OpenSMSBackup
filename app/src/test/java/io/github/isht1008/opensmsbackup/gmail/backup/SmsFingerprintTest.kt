package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.sms.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SmsFingerprintTest {
    @Test fun `fingerprint is stable across row and thread ids and phone formatting`() {
        val first = message(1, 10, "+1 (555) 123-4567", 1_000, "hello", 1)
        val second = message(99, 88, "001-555-123-4567", 1_000, "hello", 1)

        assertEquals(SmsFingerprint.generate(first), SmsFingerprint.generate(second))
    }

    @Test fun `direction timestamp and body participate in fingerprint`() {
        val base = message(1, 1, "12345", 1_000, "hello", 1)

        assertNotEquals(SmsFingerprint.generate(base), SmsFingerprint.generate(base.copy(type = 2)))
        assertNotEquals(SmsFingerprint.generate(base), SmsFingerprint.generate(base.copy(date = 1_001)))
        assertNotEquals(SmsFingerprint.generate(base), SmsFingerprint.generate(base.copy(body = "other")))
    }

    private fun message(
        id: Long,
        threadId: Long,
        address: String,
        date: Long,
        body: String,
        type: Int
    ) = SmsMessage(id, threadId, address, null, body, date, "", type)
}
