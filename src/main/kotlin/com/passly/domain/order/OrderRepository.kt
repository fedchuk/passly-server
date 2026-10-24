package com.passly.domain.order

import com.passly.domain.floor.DiningTableId
import com.passly.domain.venue.VenueId

interface OrderRepository {
    fun find(venueId: VenueId, id: OrderId): Order?
    fun findForUpdate(venueId: VenueId, id: OrderId): Order?
    fun findOpenAtTable(tableId: DiningTableId): Order?
    fun findOpen(venueId: VenueId): List<Order>
    fun existsAtTable(tableId: DiningTableId): Boolean
    fun save(order: Order)
}
