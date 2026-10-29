package com.passly.domain.order

import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.discount.DiscountList
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.discount.VenueDiscount
import com.passly.domain.menu.MenuItem
import com.passly.domain.user.UserId
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.VenueId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OrderDiscountTest {

    private val fixtures = OrderFixtures()
    private val waiter = UserId.new()
    private val now = Instant.parse("2026-10-29T20:00:00Z")
    private val discounts = DiscountList(fixtures.venueId, emptyList())
    private val regular = discount("Regular guest", "10")
    private val coffee = fixtures.item("Espresso", "1.80")
    private val wine = fixtures.item("Bordeaux", "6.50", taxRate = "20")
    private val steak = fixtures.item("Entrecôte", "28.00")

    private fun discount(name: String, percent: String): VenueDiscount =
        discounts.add(fixtures.tenantId, name, DiscountPercent.of(percent))

    private fun priced(item: MenuItem, quantity: Int = 1) =
        item.priceLine(OrderLineRequest(item.id, quantity), emptyMap(), null, fixtures.taxRateOf(item))

    private fun orderWith(vararg lines: PricedLine) =
        Order.open(fixtures.table(), 2, waiter, now).apply { addRound(lines.toList(), waiter, now) }

    private fun chf(amount: String) = Tender.main(Money.of(amount), CurrencyCode.of("CHF"))

    @Test
    fun `discount is taken off the whole bill and copied into the order`() {
        val order = orderWith(priced(steak), priced(wine, 2))

        order.applyDiscount(regular, waiter, now)
        discounts.change(regular.id, "Friends", DiscountPercent.of("50"))

        assertEquals(Money.of("41.00"), order.total)
        assertEquals(Money.of("4.10"), order.discountAmount)
        assertEquals(Money.of("36.90"), order.toPay)
        assertEquals(Money.of("36.90"), order.due)
        assertEquals(OrderDiscount(regular.id, "Regular guest", DiscountPercent.of("10"), waiter, now), order.discount)
    }

    @Test
    fun `vat is counted on the discounted amount of every rate`() {
        val order = orderWith(priced(steak), priced(wine, 2))

        order.applyDiscount(regular, waiter, now)

        val shares = order.taxShares.associate { it.rate.percent.toPlainString() to it }
        assertEquals(Money.of("25.20"), shares.getValue("10.00").gross)
        assertEquals(Money.of("2.29"), shares.getValue("10.00").tax)
        assertEquals(Money.of("11.70"), shares.getValue("20.00").gross)
        assertEquals(Money.of("1.95"), shares.getValue("20.00").tax)
    }

    @Test
    fun `rounding cents go to the largest rate so the shares add up to what is paid`() {
        val water = fixtures.item("Water", "0.05")
        val shot = fixtures.item("Shot", "0.05", taxRate = "20")
        val order = orderWith(priced(water), priced(shot))

        order.applyDiscount(discount("Half", "50"), waiter, now)

        assertEquals(Money.of("0.05"), order.toPay)
        assertEquals(order.toPay, order.taxShares.fold(Money.ZERO) { sum, share -> sum + share.gross })
    }

    @Test
    fun `next round is discounted too`() {
        val order = orderWith(priced(coffee))
        order.applyDiscount(regular, waiter, now)

        order.addRound(listOf(priced(steak)), waiter, now)

        assertEquals(Money.of("26.82"), order.toPay)
    }

    @Test
    fun `discount can be replaced and removed until the first payment`() {
        val order = orderWith(priced(steak))
        order.applyDiscount(regular, waiter, now)
        order.applyDiscount(discount("Staff meal", "50"), waiter, now)
        assertEquals(Money.of("14.00"), order.due)

        order.removeDiscount()
        assertNull(order.discount)
        order.applyDiscount(regular, waiter, now)
        order.pay(PaymentMethod.CASH, chf("10.00"), waiter, now)

        assertFailsWith<ConflictException> { order.removeDiscount() }
        assertFailsWith<ConflictException> { order.applyDiscount(discount("Owner", "100"), waiter, now) }
        assertEquals(Money.of("15.20"), order.due)
    }

    @Test
    fun `the payment of the discounted rest closes the order`() {
        val order = orderWith(priced(steak))
        order.applyDiscount(regular, waiter, now)

        order.pay(PaymentMethod.CARD, chf("25.20"), waiter, now)

        assertEquals(OrderStatus.CLOSED, order.status)
    }

    @Test
    fun `a free bill is closed instead of paid`() {
        val order = orderWith(priced(steak))
        order.applyDiscount(discount("On the house", "100"), waiter, now)

        assertFailsWith<ConflictException> { order.pay(PaymentMethod.CASH, chf("1.00"), waiter, now) }
        order.close(waiter, now)

        assertEquals(OrderStatus.CLOSED, order.status)
        assertEquals(Money.ZERO, order.taxShares.single().tax)
    }

    @Test
    fun `discount of another venue is refused`() {
        val order = orderWith(priced(steak))
        val foreign = DiscountList(VenueId.new(), emptyList()).add(fixtures.tenantId, "Elsewhere", DiscountPercent.of("10"))

        assertFailsWith<IllegalArgumentException> { order.applyDiscount(foreign, waiter, now) }
    }
}
