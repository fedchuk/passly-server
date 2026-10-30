package com.passly.domain.voucher

import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.order.OrderId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoucherTest {

    private val today = LocalDate.parse("2026-10-30")
    private val now = Instant.parse("2026-10-30T19:00:00Z")
    private val order = OrderId.new()
    private val otherOrder = OrderId.new()

    private fun voucher(kind: VoucherKind, validUntil: LocalDate = today.plusDays(10)) = Voucher.create(
        TenantId.new(), VenueId.new(), VoucherCode.parse("ABCD-2345"), " Birthday gift ", kind,
        VoucherValue.Amount(Money.of("20.00")), validUntil, today, now,
    )

    @Test
    fun `voucher cannot be created already expired`() {
        assertFailsWith<ValidationException> { voucher(VoucherKind.REUSABLE, today.minusDays(1)) }
        assertEquals("Birthday gift", voucher(VoucherKind.REUSABLE, today).name)
    }

    @Test
    fun `voucher works through its last day`() {
        val lastDay = voucher(VoucherKind.REUSABLE, today)

        lastDay.redeem(order, now, today)

        val error = assertFailsWith<ConflictException> { lastDay.redeem(order, now, today.plusDays(1)) }
        assertEquals("Voucher ABCD-2345 expired on 2026-10-30", error.message)
    }

    @Test
    fun `reusable voucher serves any number of bills`() {
        val voucher = voucher(VoucherKind.REUSABLE)

        voucher.redeem(order, now, today)
        voucher.redeem(otherOrder, now, today)

        assertNull(voucher.redeemedOrderId)
    }

    @Test
    fun `single-use voucher belongs to one bill until it is released`() {
        val voucher = voucher(VoucherKind.SINGLE_USE)

        voucher.redeem(order, now, today)
        voucher.redeem(order, now, today)
        assertFailsWith<ConflictException> { voucher.redeem(otherOrder, now, today) }
        assertFailsWith<ConflictException> { voucher.requireDeletable() }

        voucher.release(otherOrder)
        assertEquals(order, voucher.redeemedOrderId)

        voucher.release(order)
        voucher.redeem(otherOrder, now, today)
        assertEquals(otherOrder, voucher.redeemedOrderId)
        assertEquals(now, voucher.redeemedAt)
    }

    @Test
    fun `values take percent or a capped amount`() {
        val percent = VoucherValue.Percent(DiscountPercent.of("15"))
        val amount = VoucherValue.Amount(Money.of("20.00"))

        assertEquals(Money.of("4.50"), percent.of(Money.of("30.00")))
        assertEquals(Money.of("20.00"), amount.of(Money.of("30.00")))
        assertEquals(Money.of("12.00"), amount.of(Money.of("12.00")))
        assertFailsWith<ValidationException> { VoucherValue.Amount(Money.ZERO) }
        assertFailsWith<ValidationException> { VoucherValue.Amount(Money.of("100000.01")) }
        assertTrue(amount.label == "20.00" && percent.label == "15%")
    }
}
