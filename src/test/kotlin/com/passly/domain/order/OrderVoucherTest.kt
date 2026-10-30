package com.passly.domain.order

import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.discount.DiscountList
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.menu.MenuItem
import com.passly.domain.user.UserId
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.voucher.Voucher
import com.passly.domain.voucher.VoucherCode
import com.passly.domain.voucher.VoucherKind
import com.passly.domain.voucher.VoucherValue
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OrderVoucherTest {

    private val fixtures = OrderFixtures()
    private val waiter = UserId.new()
    private val now = Instant.parse("2026-10-30T20:00:00Z")
    private val today = LocalDate.parse("2026-10-30")
    private val steak = fixtures.item("Entrecôte", "28.00")
    private val wine = fixtures.item("Bordeaux", "6.50", taxRate = "20")
    private val regular = DiscountList(fixtures.venueId, emptyList())
        .add(fixtures.tenantId, "Regular guest", DiscountPercent.of("10"))

    private fun voucher(value: VoucherValue, code: String = "ABCD-2345") = Voucher.create(
        fixtures.tenantId, fixtures.venueId, VoucherCode.parse(code), "Gift", VoucherKind.SINGLE_USE,
        value, today, today, now,
    )

    private fun amount(text: String) = VoucherValue.Amount(Money.of(text))

    private fun priced(item: MenuItem, quantity: Int = 1) =
        item.priceLine(OrderLineRequest(item.id, quantity), emptyMap(), null, fixtures.taxRateOf(item))

    private fun orderWith(vararg lines: PricedLine) =
        Order.open(fixtures.table(), 2, waiter, now).apply { addRound(lines.toList(), waiter, now) }

    @Test
    fun `voucher is taken off after the discount`() {
        val order = orderWith(priced(steak), priced(wine, 2))

        order.applyDiscount(regular, waiter, now)
        order.applyVoucher(voucher(VoucherValue.Percent(DiscountPercent.of("50"))), waiter, now)

        assertEquals(Money.of("41.00"), order.total)
        assertEquals(Money.of("4.10"), order.discountAmount)
        assertEquals(Money.of("36.90"), order.afterDiscount)
        assertEquals(Money.of("18.45"), order.voucherAmount)
        assertEquals(Money.of("18.45"), order.toPay)
        assertEquals("ABCD-2345", order.voucher?.code?.label)
    }

    @Test
    fun `amount voucher never takes more than the bill`() {
        val order = orderWith(priced(wine))

        order.applyVoucher(voucher(amount("25.00")), waiter, now)

        assertEquals(Money.of("6.50"), order.voucherAmount)
        assertEquals(Money.ZERO, order.due)
        assertFailsWith<ConflictException> { order.pay(PaymentMethod.CASH, Tender.main(Money.of("1.00"), CurrencyCode.of("EUR")), waiter, now) }
        order.close(waiter, now)
        assertEquals(OrderStatus.CLOSED, order.status)
    }

    @Test
    fun `vat follows what is paid and the shares add up`() {
        val order = orderWith(priced(steak), priced(wine, 2))

        order.applyVoucher(voucher(amount("20.00")), waiter, now)

        val shares = order.taxShares
        assertEquals(Money.of("21.00"), order.toPay)
        assertEquals(order.toPay, shares.fold(Money.ZERO) { sum, share -> sum + share.gross })
        assertEquals(Money.of("14.34"), shares.single { it.rate.percent.toPlainString() == "10.00" }.gross)
        assertEquals(Money.of("6.66"), shares.single { it.rate.percent.toPlainString() == "20.00" }.gross)
    }

    @Test
    fun `voucher is replaced, removed and frozen after the first payment`() {
        val order = orderWith(priced(steak))
        order.applyVoucher(voucher(amount("5.00")), waiter, now)
        order.applyVoucher(voucher(amount("8.00"), "WXYZ-6789"), waiter, now)
        assertEquals(Money.of("20.00"), order.due)

        order.removeVoucher()
        assertNull(order.voucher)
        order.applyVoucher(voucher(amount("8.00")), waiter, now)
        order.pay(PaymentMethod.CARD, Tender.main(Money.of("10.00"), CurrencyCode.of("EUR")), waiter, now)

        val error = assertFailsWith<ConflictException> { order.removeVoucher() }
        assertEquals("The voucher cannot change after the first payment", error.message)
        assertFailsWith<ConflictException> { order.removeDiscount() }
    }
}
