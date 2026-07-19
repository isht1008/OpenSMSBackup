package io.github.isht1008.opensmsbackup.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsAddressNormalizerTest {
    private val normalizer = SmsAddressNormalizer()

    @Test fun `Indian national and international formats share E164 identity`() {
        val values = listOf(
            "9876543210",
            "09876543210",
            "+91 98765 43210",
            "0091 98765 43210"
        ).map { normalizer.normalize(it, "IN") }

        assertTrue(values.all { it is NormalizedSmsAddress.PhoneNumber })
        assertEquals(setOf("+919876543210"), values.map { it.canonical }.toSet())
    }

    @Test fun `sender ids are classified conservatively and normalized consistently`() {
        val upper = normalizer.normalize("VM-AMAZON", "IN")
        val lower = normalizer.normalize(" vm-amazon ", "IN")

        assertTrue(upper is NormalizedSmsAddress.SenderId)
        assertEquals(upper.canonical, lower.canonical)
        assertNotEquals(
            normalizer.normalize("AX-HDFCBK", "IN").canonical,
            normalizer.normalize("HDFCBK", "IN").canonical
        )
        assertNotEquals(
            normalizer.normalize("VM-HDFCBK", "IN").canonical,
            normalizer.normalize("HDFCBK", "IN").canonical
        )
        assertNotEquals(
            normalizer.normalize("AX-HDFCBK", "IN").canonical,
            normalizer.normalize("VM-HDFCBK", "IN").canonical
        )
    }

    @Test fun `short malformed empty and null addresses are safe and deterministic`() {
        assertTrue(normalizer.normalize("12345", "IN") is NormalizedSmsAddress.ShortCode)
        assertTrue(normalizer.normalize("++--", "IN") is NormalizedSmsAddress.Unknown)
        assertEquals(
            normalizer.normalize(null, "IN").canonical,
            normalizer.normalize("", "IN").canonical
        )
    }
}
