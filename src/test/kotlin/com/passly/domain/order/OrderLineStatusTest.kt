package com.passly.domain.order

import com.passly.domain.common.Money
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.menu.MenuItem
import com.passly.domain.user.UserId
import com.passly.domain.venue.CurrencyCode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderLineStatusTest {

    private val fixtures = OrderFixtures()
    private val waiter = UserId.new()
    private val sentAt = Instant.parse("2026-10-30T19:00:00Z")
    private val later = sentAt.plusSeconds(300)
    private val steak = fixtures.item("Entrecôte", "28.00")
    private val wine = fixtures.item("Bordeaux", "6.50", taxRate = "20")

    private fun priced(item: MenuItem) =
        item.priceLine(OrderLineRequest(item.id, 1), emptyMap(), null, fixtures.taxRateOf(item))

    private fun order() = Order.open(fixtures.table(), 2, waiter, sentAt).apply { addRound(listOf(priced(steak), priced(wine)), waiter, sentAt) }

    @Test
    fun `sent lines start as new`() {
        val order = order()

        assertTrue(order.lines.all { it.status == PrepStatus.NEW && it.statusChangedAt == sentAt })
    }

    @Test
    fun `lines move forward and may skip a step`() {
        val order = order()
        val (steakLine, wineLine) = order.lines

        val moved = order.moveLines(listOf(steakLine.id), PrepStatus.IN_PROGRESS, later)
        order.moveLines(listOf(wineLine.id), PrepStatus.READY, later)

        assertEquals(listOf(steakLine.id), moved.map { it.id })
        assertEquals(listOf(PrepStatus.IN_PROGRESS, PrepStatus.READY), order.lines.map { it.status })
        assertEquals(later, order.lines.first().statusChangedAt)
    }

    @Test
    fun `a line never goes back and repeating a move changes nothing`() {
        val order = order()
        val ids = order.lines.map { it.id }
        order.moveLines(ids, PrepStatus.READY, later)

        val repeated = order.moveLines(ids, PrepStatus.READY, later.plusSeconds(60))
        val backwards = order.moveLines(ids, PrepStatus.IN_PROGRESS, later.plusSeconds(60))

        assertTrue(repeated.isEmpty() && backwards.isEmpty())
        assertTrue(order.lines.all { it.status == PrepStatus.READY && it.statusChangedAt == later })
        assertFailsWith<ValidationException> { order.moveLines(ids, PrepStatus.NEW, later) }
        assertFailsWith<ValidationException> { order.moveLines(emptyList(), PrepStatus.READY, later) }
        assertFailsWith<NotFoundException> { order.moveLines(listOf(OrderLineId.new()), PrepStatus.READY, later) }
    }

    @Test
    fun `lines of a paid order can still be prepared`() {
        val order = order()
        order.pay(PaymentMethod.CARD, Tender.main(Money.of("34.50"), CurrencyCode.of("EUR")), waiter, later)

        order.moveLines(order.lines.map { it.id }, PrepStatus.SERVED, later)

        assertEquals(OrderStatus.CLOSED, order.status)
        assertTrue(order.lines.all { it.status == PrepStatus.SERVED })
    }

    @Test
    fun `statuses follow each other`() {
        assertEquals(PrepStatus.IN_PROGRESS, PrepStatus.NEW.next)
        assertEquals(null, PrepStatus.SERVED.next)
        assertTrue(PrepStatus.NEW.isBefore(PrepStatus.READY))
    }
}
