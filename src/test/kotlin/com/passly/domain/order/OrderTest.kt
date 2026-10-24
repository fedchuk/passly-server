package com.passly.domain.order

import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.ValidationException
import com.passly.domain.floor.FloorPlan
import com.passly.domain.user.UserId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderTest {

    private val fixtures = OrderFixtures()
    private val waiter = UserId.new()
    private val now = Instant.parse("2026-10-24T12:00:00Z")
    private val coffee = fixtures.item("Espresso", "1.80")
    private val cake = fixtures.item("Cheesecake", "7.50")

    private fun priced(item: com.passly.domain.menu.MenuItem, quantity: Int = 1) =
        item.priceLine(OrderLineRequest(item.id, quantity), emptyMap(), null)

    @Test
    fun `a new order is open and empty`() {
        val table = fixtures.table("7")

        val order = Order.open(table, guests = 2, waiter, now)

        assertEquals(OrderStatus.OPEN, order.status)
        assertEquals(table.id, order.tableId)
        assertEquals(fixtures.venueId, order.venueId)
        assertEquals(0, order.rounds)
        assertEquals(Money.ZERO, order.total)
        assertTrue(order.isNew)
    }

    @Test
    fun `each round gets the next number and all lines of it share it`() {
        val order = Order.open(fixtures.table(), null, waiter, now)

        assertEquals(1, order.addRound(listOf(priced(coffee, 2), priced(cake)), waiter, now))
        assertEquals(2, order.addRound(listOf(priced(coffee)), waiter, now.plusSeconds(600)))

        assertEquals(listOf(1, 1, 2), order.lines.map { it.round })
        assertEquals(Money.of("12.90"), order.total)
        assertEquals(now.plusSeconds(600), order.lines.last().addedAt)
    }

    @Test
    fun `an inactive table cannot be opened`() {
        val floor = FloorPlan(fixtures.venueId, emptyList(), emptyList())
        val area = floor.addArea(fixtures.tenantId, "Terrace")
        val table = floor.addTable(fixtures.tenantId, area.id, "T1", 2)
        floor.updateTable(table.id, area.id, "T1", 2, active = false)

        assertFailsWith<ConflictException> { Order.open(table, null, waiter, now) }
    }

    @Test
    fun `guests and round size are validated`() {
        assertFailsWith<ValidationException> { Order.open(fixtures.table("1"), 0, waiter, now) }
        assertFailsWith<ValidationException> { Order.open(fixtures.table("2"), Order.MAX_GUESTS + 1, waiter, now) }

        val order = Order.open(fixtures.table("3"), null, waiter, now)
        assertFailsWith<ValidationException> { order.addRound(emptyList(), waiter, now) }
        assertFailsWith<ValidationException> {
            order.addRound(List(Order.MAX_LINES_PER_ROUND + 1) { priced(coffee) }, waiter, now)
        }
        assertEquals(0, order.lines.size)
    }
}