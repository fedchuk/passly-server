package com.passly.application.order

import com.passly.domain.common.NotFoundException
import com.passly.domain.floor.DiningTableId
import com.passly.domain.floor.FloorPlanRepository
import com.passly.domain.order.OrderRepository
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class FloorStatusQuery(
    private val venues: VenueRepository,
    private val plans: FloorPlanRepository,
    private val orders: OrderRepository,
) {

    fun floorOf(venueId: VenueId): FloorStatus {
        requireVenue(venueId)
        return FloorStatus(plans.load(venueId), orders.findOpen(venueId).associateBy { it.tableId })
    }

    fun tableOf(venueId: VenueId, tableId: DiningTableId): TableStatus {
        requireVenue(venueId)
        val plan = plans.load(venueId)
        val table = plan.table(tableId)
        return TableStatus(table, plan.area(table.areaId), orders.findOpenAtTable(table.id))
    }

    private fun requireVenue(id: VenueId) {
        venues.findById(id) ?: throw NotFoundException("Venue", id)
    }
}
