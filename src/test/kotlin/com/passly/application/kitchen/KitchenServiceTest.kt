package com.passly.application.kitchen

import com.passly.application.menu.FakeStationRepository
import com.passly.application.menu.FakeVenueRepository
import com.passly.application.order.FakeOrderRepository
import com.passly.domain.common.Money
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.TaxRate
import com.passly.domain.floor.FloorPlan
import com.passly.domain.menu.MenuItemId
import com.passly.domain.menu.StationId
import com.passly.domain.order.*
import com.passly.domain.user.UserId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KitchenServiceTest {

    private val venue = Venue(VenueId.new(), TenantId.new(), "Bistro")
    private val bar = StationId.new()
    private val now = Instant.parse("2026-10-30T20:00:00Z")
    private val orders = FakeOrderRepository()
    private val stations = FakeStationRepository(mapOf(bar to venue.id))
    private val board = RecordingBoardQuery()
    private val service = KitchenService(FakeVenueRepository(venue), stations, orders, board, Clock.fixed(now, ZoneOffset.UTC))
    private val waiter = UserId.new()

    private fun order(): Order {
        val plan = FloorPlan(venue.id, emptyList(), emptyList())
        val table = plan.addTable(venue.tenantId, plan.addArea(venue.tenantId, "Bar").id, "B1", 2)
        val line = PricedLine(MenuItemId.new(), "Mojito", Money.of("9.00"), TaxRate.of("20"), bar, 2, null, emptyList())
        return Order.open(table, 2, waiter, now.minusSeconds(600))
            .apply { addRound(listOf(line), waiter, now.minusSeconds(600)) }
            .also(orders::save)
    }

    @Test
    fun `board looks back one day at the chosen station`() {
        service.tickets(venue.id, bar)

        assertEquals(Triple(venue.id, bar, now.minusSeconds(86_400)), board.asked)
        assertFailsWith<NotFoundException> { service.tickets(venue.id, StationId.new()) }
    }

    @Test
    fun `lines are moved under a lock of the order`() {
        val order = order()

        val moved = service.moveLines(MoveLinesCommand(venue.id, order.id, order.lines.map { it.id }, PrepStatus.READY))

        assertEquals(1, moved.size)
        assertEquals(PrepStatus.READY, orders.stored.getValue(order.id).lines.single().status)
        assertEquals(now, orders.stored.getValue(order.id).lines.single().statusChangedAt)
        assertEquals(1, orders.locks)
        assertFailsWith<NotFoundException> {
            service.moveLines(MoveLinesCommand(venue.id, OrderId.new(), listOf(OrderLineId.new()), PrepStatus.READY))
        }
    }

    private class RecordingBoardQuery : StationBoardQuery {
        var asked: Triple<VenueId, StationId, Instant>? = null

        override fun ticketsOf(venueId: VenueId, stationId: StationId, since: Instant): List<StationTicket> {
            asked = Triple(venueId, stationId, since)
            return emptyList()
        }
    }
}
