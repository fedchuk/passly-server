package com.passly.domain.discount

import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiscountPercentTest {

    @Test
    fun `percent is above zero and at most a hundred`() {
        assertFailsWith<ValidationException> { DiscountPercent.of("0") }
        assertFailsWith<ValidationException> { DiscountPercent.of("-5") }
        assertFailsWith<ValidationException> { DiscountPercent.of("100.01") }
        assertEquals("100%", DiscountPercent.of("100").label)
    }

    @Test
    fun `percent has at most two decimal places`() {
        assertFailsWith<ValidationException> { DiscountPercent.of("12.345") }
        assertEquals("12.5%", DiscountPercent.of("12.50").label)
    }

    @Test
    fun `amount off is rounded half up to cents`() {
        assertEquals(Money.of("0.03"), DiscountPercent.of("50").of(Money.of("0.05")))
        assertEquals(Money.of("3.19"), DiscountPercent.of("10").of(Money.of("31.90")))
        assertEquals(Money.of("31.90"), DiscountPercent.of("100").of(Money.of("31.90")))
    }
}
