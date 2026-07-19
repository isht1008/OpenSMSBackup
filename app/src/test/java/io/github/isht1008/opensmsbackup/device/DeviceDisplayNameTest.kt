package io.github.isht1008.opensmsbackup.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDisplayNameTest {
    @Test fun `marketing name wins when available`() {
        assertEquals("S25 Ultra", DeviceDisplayName.initialBaseName("S25 Ultra", "Samsung", "SM-X"))
    }

    @Test fun `manufacturer and model provide readable fallback without phone`() {
        assertEquals("Samsung SM-G990B", DeviceDisplayName.initialBaseName(null, "Samsung", "SM-G990B"))
    }

    @Test fun `only last five phone digits enter display and label`() {
        val result = DeviceDisplayName.withPhoneSuffix("S25 Ultra", "+91 98765 57605")
        assertEquals("S25 Ultra (57605)", result)
        assertFalse(result.contains("98765"))
    }

    @Test fun `duplicate readable name gets deterministic stable suffix`() {
        assertEquals(
            "Work Phone • A7C2",
            DeviceDisplayName.gmailLabelSegment("Work Phone", null, "a7c2-ffff", true)
        )
    }

    @Test fun `unsafe Gmail label characters and whitespace are sanitized`() {
        val result = DeviceDisplayName.sanitizeLabelSegment("  Work/Phone\\\n\u0000  Test  ")
        assertEquals("Work Phone Test", result)
        assertFalse(result.contains('/'))
    }

    @Test fun `masked number never exposes more than last five digits`() {
        val masked = requireNotNull(DeviceDisplayName.maskedNumber("+91 98765 57605"))
        assertEquals("•••••57605", masked)
        assertTrue(masked.endsWith("57605"))
    }

    @Test fun `phone number pasted into device name is reduced to last five digits`() {
        val result = DeviceDisplayName.sanitizeLabelSegment("Phone +91 98765 57605")
        assertEquals("Phone (57605)", result)
        assertFalse(result.contains("98765"))
    }
}
