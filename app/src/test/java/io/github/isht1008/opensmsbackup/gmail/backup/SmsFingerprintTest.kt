package io.github.isht1008.opensmsbackup.gmail.backup

import io.github.isht1008.opensmsbackup.sms.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SmsFingerprintTest {
    @Test fun `V1 fingerprint output is unchanged`() {
        val value = message(1, 10, "+1 (555) 123-4567", 1_000, "hello", 1)
        assertEquals(
            "f42e9fb47d264f341d0f89f1b6e34b84be49e930c5893b0867fe3fb973e8187e",
            SmsFingerprint.generate(value, SmsFingerprintVersion.V1_LEGACY, "IN")
        )
    }

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

    @Test fun `V2 fingerprint uses country aware address and remains deterministic`() {
        val national = message(1, 1, "09876543210", 1_000, "hello", 1)
        val international = national.copy(id = 99, threadId = 88, address = "+91 98765 43210")
        fun fingerprint(value: SmsMessage) = SmsFingerprint.generate(
            value, SmsFingerprintVersion.V2_COUNTRY_AWARE, "IN"
        )

        assertEquals(fingerprint(national), fingerprint(international))
        assertEquals(fingerprint(national), fingerprint(national))
        assertNotEquals(fingerprint(national), fingerprint(national.copy(type = 2)))
        assertNotEquals(fingerprint(national), fingerprint(national.copy(date = 1_001)))
        assertNotEquals(fingerprint(national), fingerprint(national.copy(body = "other")))
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
