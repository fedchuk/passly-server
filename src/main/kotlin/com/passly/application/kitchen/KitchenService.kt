package com.passly.application.kitchen

import com.passly.domain.common.NotFoundException
import com.passly.domain.menu.Station
import com.passly.domain.menu.StationId
import com.passly.domain.menu.StationRepository
import com.passly.domain.order.OrderLine
import com.passly.domain.order.OrderRepository
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Service
@Transactional
class KitchenService(
    private val venues: VenueRepository,
    private val stations: StationRepository,
    private val orders: OrderRepository,
    private val board: StationBoardQuery,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun stations(venueId: VenueId): List<Station> {
        requireVenue(venueId)
        return stations.findAllInVenue(venueId)
    }

    @Transactional(readOnly = true)
    fun station(venueId: VenueId, stationId: StationId): Station {
        requireVenue(venueId)
        return stations.load(venueId)[stationId]
    }

    @Transactional(readOnly = true)
    fun tickets(venueId: VenueId, stationId: StationId): List<StationTicket> {
        station(venueId, stationId)
        return board.ticketsOf(venueId, stationId, clock.instant().minus(BOARD_WINDOW))
    }

    fun moveLines(command: MoveLinesCommand): List<OrderLine> {
        val order = orders.findForUpdate(command.venueId, command.orderId) ?: throw NotFoundException("Order", command.orderId)
        val moved = order.moveLines(command.lineIds, command.status, clock.instant())
        orders.save(order)
        return moved
    }

    private fun requireVenue(id: VenueId) {
        venues.findById(id) ?: throw NotFoundException("Venue", id)
    }

    companion object {
        val BOARD_WINDOW: Duration = Duration.ofHours(24)
    }
}
