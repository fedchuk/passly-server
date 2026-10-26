package com.passly.domain.venue

import com.passly.domain.common.ValidationException
import kotlin.test.*

class CurrencyCodeTest {

    @Test
    fun `code is normalized to upper case`() {
        assertEquals("CHF", CurrencyCode.of("chf").value)
    }

    @Test
    fun `unknown currency is refused`() {
        val error = assertFailsWith<ValidationException> { CurrencyCode.of("ABC") }
        assertEquals("currency", error.field)
    }

    @Test
    fun `currency that is no longer in use is refused`() {
        assertFailsWith<ValidationException> { CurrencyCode.of("FRF") }
    }

    @Test
    fun `currency without cents is refused`() {
        val error = assertFailsWith<ValidationException> { CurrencyCode.of("JPY") }
        assertEquals("currency", error.field)
    }

    @Test
    fun `supported list has two decimal currencies only`() {
        val supported = CurrencyCode.supported().map { it.value }
        assertTrue("EUR" in supported)
        assertTrue("CHF" in supported)
        assertFalse("JPY" in supported)
        assertFalse("FRF" in supported)
        assertEquals(supported.sorted(), supported)
    }
}
