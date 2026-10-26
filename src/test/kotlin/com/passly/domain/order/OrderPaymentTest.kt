package com.passly.domain.order

import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.common.ValidationException
import com.passly.domain.menu.MenuItem
import com.passly.domain.user.UserId
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.ExchangeRate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OrderPaymentTest {

    private val fixtures = OrderFixtures()
    private val waiter = UserId.new()
    private val manager = UserId.new()
    private val now = Instant.parse("2026-10-26T21:00:00Z")
    private val coffee = fixtures.item("Expresso", "1.80")
    private val wine = fixtures.item("Bordeaux, verre", "6.50", taxRate = "20")
    private val steak = fixtures.item("Entrecôte", "28.00")

    private fun chf(amount: Money) = Tender.main(amount, CurrencyCode.of("CHF"))

    private fun eur(amount: String) = Tender(Money.of(amount), CurrencyCode.of("EUR"), ExchangeRate.of("0.95"))

    private fun priced(item: MenuItem, quantity: Int = 1) =
        item.priceLine(OrderLineRequest(item.id, quantity), emptyMap(), null)

    private fun orderWith(vararg lines: PricedLine) =
        Order.open(fixtures.table(), 2, waiter, now).apply { addRound(lines.toList(), waiter, now) }

    @Test
    fun `partial payments reduce what is due and the last one closes the order`() {
        val order = orderWith(priced(steak), priced(wine, 2))

        order.pay(PaymentMethod.CASH, chf(Money.of("20.00")), waiter, now)

        assertEquals(OrderStatus.OPEN, order.status)
        assertEquals(Money.of("21.00"), order.due)

        order.pay(PaymentMethod.CARD, chf(Money.of("21.00")), manager, now.plusSeconds(60))

        assertEquals(OrderStatus.CLOSED, order.status)
        assertEquals(Money.ZERO, order.due)
        assertEquals(manager, order.closedBy)
        assertEquals(now.plusSeconds(60), order.closedAt)
        assertEquals(listOf(PaymentMethod.CASH, PaymentMethod.CARD), order.payments.map { it.method })
    }

    @Test
    fun `amount must be positive and a card cannot pay more than due`() {
        val order = orderWith(priced(coffee))

        assertFailsWith<ValidationException> { order.pay(PaymentMethod.CASH, chf(Money.ZERO), waiter, now) }
        assertFailsWith<ValidationException> { order.pay(PaymentMethod.CASH, chf(Money.of("-1.00")), waiter, now) }
        assertFailsWith<ValidationException> { order.pay(PaymentMethod.CARD, chf(Money.of("1.81")), waiter, now) }
        assertEquals(emptyList(), order.payments)
    }

    @Test
    fun `cash above what is due counts the due and records the change`() {
        val order = orderWith(priced(coffee))

        val payment = order.pay(PaymentMethod.CASH, chf(Money.of("5.00")), waiter, now)

        assertEquals(Money.of("1.80"), payment.amount)
        assertEquals(Money.of("3.20"), payment.change)
        assertEquals(OrderStatus.CLOSED, order.status)
    }

    @Test
    fun `foreign cash is converted and the change is in the main currency`() {
        val order = orderWith(priced(steak), priced(wine, 2))

        val payment = order.pay(PaymentMethod.CASH, eur("50.00"), waiter, now)

        assertEquals(Money.of("41.00"), payment.amount)
        assertEquals(Money.of("6.50"), payment.change)
        assertEquals(eur("50.00"), payment.tender)
        assertEquals(OrderStatus.CLOSED, order.status)
    }

    @Test
    fun `foreign part payment reduces the due by its converted value`() {
        val order = orderWith(priced(steak), priced(wine, 2))

        val payment = order.pay(PaymentMethod.CARD, eur("20.00"), waiter, now)

        assertEquals(Money.of("19.00"), payment.amount)
        assertEquals(Money.ZERO, payment.change)
        assertEquals(Money.of("22.00"), order.due)
    }

    @Test
    fun `less than one foreign cent over the due settles exactly`() {
        val order = orderWith(priced(fixtures.item("Tarte", "45.30")))

        val payment = order.pay(PaymentMethod.CARD, Tender(Money.of("47.69"), CurrencyCode.of("EUR"), ExchangeRate.of("0.95")), waiter, now)

        assertEquals(Money.of("45.30"), payment.amount)
        assertEquals(Money.ZERO, payment.change)
        assertEquals(OrderStatus.CLOSED, order.status)
    }

    @Test
    fun `foreign card payment above the due is refused`() {
        val order = orderWith(priced(coffee))

        assertFailsWith<ValidationException> { order.pay(PaymentMethod.CARD, eur("5.00"), waiter, now) }
        assertEquals(emptyList(), order.payments)
    }

    @Test
    fun `an empty order cannot be paid but can be closed`() {
        val order = Order.open(fixtures.table(), null, waiter, now)

        assertFailsWith<ValidationException> { order.pay(PaymentMethod.CARD, chf(Money.of("1.00")), waiter, now) }

        order.close(waiter, now)

        assertEquals(OrderStatus.CLOSED, order.status)
    }

    @Test
    fun `an order with something due cannot be closed without payment`() {
        val order = orderWith(priced(coffee))

        assertFailsWith<ConflictException> { order.close(waiter, now) }
        assertEquals(OrderStatus.OPEN, order.status)
        assertNull(order.closedAt)
    }

    @Test
    fun `a closed order takes no more rounds or payments`() {
        val order = orderWith(priced(coffee))
        order.pay(PaymentMethod.CARD, chf(Money.of("1.80")), waiter, now)

        assertFailsWith<ConflictException> { order.addRound(listOf(priced(coffee)), waiter, now) }
        assertFailsWith<ConflictException> { order.pay(PaymentMethod.CASH, chf(Money.of("1.00")), waiter, now) }
        assertFailsWith<ConflictException> { order.close(waiter, now) }
    }

    @Test
    fun `tax is extracted from gross prices per rate`() {
        val order = orderWith(priced(coffee), priced(steak), priced(wine))

        val shares = order.taxShares

        assertEquals(listOf(TaxRate.of("10"), TaxRate.of("20")), shares.map { it.rate })
        assertEquals(Money.of("29.80"), shares[0].gross)
        assertEquals(Money.of("2.71"), shares[0].tax)
        assertEquals(Money.of("27.09"), shares[0].net)
        assertEquals(Money.of("6.50"), shares[1].gross)
        assertEquals(Money.of("1.08"), shares[1].tax)
    }
}
