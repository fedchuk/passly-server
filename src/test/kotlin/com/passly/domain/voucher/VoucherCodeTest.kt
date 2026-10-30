package com.passly.domain.voucher

import com.passly.domain.common.ValidationException
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VoucherCodeTest {

    @Test
    fun `generated codes have eight characters without look-alikes`() {
        val random = Random(7)
        val codes = List(500) { VoucherCode.generate(random) }

        assertTrue(codes.all { it.value.length == 8 })
        assertTrue(codes.none { code -> code.value.any { it in "01IO" } })
        assertTrue(codes.toSet().size > 495)
    }

    @Test
    fun `code is read with or without the dash, spaces and case`() {
        val code = VoucherCode.parse(" abcd-2345 ")

        assertEquals("ABCD2345", code.value)
        assertEquals("ABCD-2345", code.label)
        assertEquals(code, VoucherCode.parse("ABCD 2345"))
    }

    @Test
    fun `malformed codes are refused`() {
        assertFailsWith<ValidationException> { VoucherCode.parse("ABCD-234") }
        assertFailsWith<ValidationException> { VoucherCode.parse("ABCD-2340") }
        assertFailsWith<ValidationException> { VoucherCode.parse("") }
    }
}
