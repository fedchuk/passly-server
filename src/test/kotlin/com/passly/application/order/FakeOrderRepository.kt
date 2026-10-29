package com.passly.application.order

import com.passly.domain.floor.DiningTableId
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderId
import com.passly.domain.order.OrderRepository
import com.passly.domain.order.OrderStatus
import com.passly.domain.venue.VenueId

class FakeOrderRepository : OrderRepository {
    val stored = linkedMapOf<OrderId, Order>()
    var locks = 0

    override fun find(venueId: VenueId, id: OrderId) = stored[id]?.takeIf { it.venueId == venueId }?.let(::copyOf)

    override fun findForUpdate(venueId: VenueId, id: OrderId) = find(venueId, id)?.also { locks++ }

    override fun findOpenAtTable(tableId: DiningTableId) =
        stored.values.firstOrNull { it.tableId == tableId && it.status == OrderStatus.OPEN }?.let(::copyOf)

    override fun existsAtStation(stationId: StationId) =
        stored.values.any { order -> order.lines.any { it.stationId == stationId } }

    override fun findOpen(venueId: VenueId) =
        stored.values.filter { it.venueId == venueId && it.status == OrderStatus.OPEN }.map(::copyOf)

    override fun existsAtTable(tableId: DiningTableId) = stored.values.any { it.tableId == tableId }
    override fun existsInVenue(venueId: VenueId) = stored.values.any { it.venueId == venueId }

    override fun save(order: Order) {
        stored[order.id] = copyOf(order)
    }

    private fun copyOf(o: Order) = Order.fromStorage(
        o.id, o.tenantId, o.venueId, o.tableId, o.status, o.guests, o.openedBy, o.openedAt,
        o.lines, o.payments, o.closedBy, o.closedAt, o.version ?: 0, o.discount,
    )
}
