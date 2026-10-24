package com.passly.domain.order

import com.passly.domain.floor.DiningTableId
import com.passly.domain.menu.StationId
import com.passly.domain.venue.VenueId

interface OrderRepository {
    fun find(venueId: VenueId, id: OrderId): Order?
    fun findForUpdate(venueId: VenueId, id: OrderId): Order?
    fun findOpenAtTable(tableId: DiningTableId): Order?
    fun existsAtStation(stationId: StationId): Boolean
    fun findOpen(venueId: VenueId): List<Order>
    fun existsAtTable(tableId: DiningTableId): Boolean
    fun save(order: Order)
}
