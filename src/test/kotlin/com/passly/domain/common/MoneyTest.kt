package com.passly.domain.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun `amounts with different scale are equal`() {
        assertEquals(Money.of("4.5"), Money.of("4.50"))
        assertEquals("4.50", Money.of("4.5").toString())
    }

    @Test
    fun `more than two decimal places is rejected`() {
        assertFailsWith<ValidationException> { Money.of("4.555") }
        assertEquals(Money.of("4.55"), Money.of("4.5500"))
    }

    @Test
    fun `amounts are compared by value`() {
        assertTrue(Money.of("10") > Money.of("9.99"))
        assertTrue(Money.of("-0.50").isNegative)
    }

    @Test
    fun `tax rate must be a percentage`() {
        assertEquals("10.00%", TaxRate.of("10").toString())
        assertFailsWith<ValidationException> { TaxRate.of("-1") }
        assertFailsWith<ValidationException> { TaxRate.of("100.01") }
    }
}
