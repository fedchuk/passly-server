package com.passly.domain.venue

import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExchangeRateTest {

    @Test
    fun `rate is shown without trailing zeros`() {
        assertEquals("0.95", ExchangeRate.of("0.950000").toString())
    }

    @Test
    fun `zero and negative rates are refused`() {
        assertFailsWith<ValidationException> { ExchangeRate.of("0") }
        assertFailsWith<ValidationException> { ExchangeRate.of("-1.2") }
    }

    @Test
    fun `rate has at most six decimal places`() {
        val error = assertFailsWith<ValidationException> { ExchangeRate.of("1.0000001") }
        assertEquals("rate", error.field)
    }

    @Test
    fun `text that is not a number is refused`() {
        assertFailsWith<ValidationException> { ExchangeRate.of("abc") }
    }

    @Test
    fun `amount converts to the main currency rounded to cents`() {
        val rate = ExchangeRate.of("0.957")

        assertEquals(Money.of("47.85"), rate.toMain(BigDecimal("50")))
        assertEquals(Money.of("0.96"), rate.toMain(BigDecimal("1.00")))
    }
}
