package com.passly.domain.venue

import com.passly.domain.common.ValidationException
import kotlin.test.*

class CountryCodeTest {

    @Test
    fun `code is normalized to upper case`() {
        assertEquals("CH", CountryCode.of(" ch ").value)
    }

    @Test
    fun `unknown country is refused`() {
        val error = assertFailsWith<ValidationException> { CountryCode.of("XX") }
        assertEquals("country", error.field)
    }

    @Test
    fun `country knows its default currency`() {
        assertEquals("EUR", CountryCode.of("FR").defaultCurrency?.value)
        assertEquals("CHF", CountryCode.of("CH").defaultCurrency?.value)
    }

    @Test
    fun `countries whose currency has no cents are not supported yet`() {
        val supported = CountryCode.supported().map { it.value }
        assertTrue("FR" in supported)
        assertTrue("CH" in supported)
        assertFalse("JP" in supported)
    }
}
