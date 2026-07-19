package io.github.isht1008.opensmsbackup.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CountryRegionTest {
    @Test fun `Indian device locale initializes India region`() {
        assertEquals("IN", CountryRegion.initial(Locale.Builder().setLanguage("en").setRegion("IN").build()))
    }

    @Test fun `supported region is uppercased and invalid region is rejected`() {
        assertEquals("GB", CountryRegion.validated(" gb "))
        assertNull(CountryRegion.validated("ZZ"))
        assertNull(CountryRegion.validated("IND"))
        assertTrue(CountryRegion.initial(Locale.Builder().setLanguage("en").build()).length == 2)
    }
}
