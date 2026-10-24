package com.passly.application.menu

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.menu.*
import com.passly.domain.order.OrderRepository
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class StationService(
    private val venues: VenueRepository,
    private val stations: StationRepository,
    private val categories: CategoryRepository,
    private val items: MenuItemRepository,
    private val orders: OrderRepository,
) {

    @Transactional(readOnly = true)
    fun list(venueId: VenueId): List<Station> = stations.findAllInVenue(venueId)

    fun create(command: CreateStationCommand): Station {
        val venue = lockVenue(command.venueId)
        val station = stations.load(venue.id).add(venue.tenantId, command.name)
        stations.saveAll(listOf(station))
        return station
    }

    fun rename(command: RenameStationCommand): Station {
        lockVenue(command.venueId)
        val list = stations.load(command.venueId)
        val current = list[command.stationId]
        if (command.expectedVersion != null && command.expectedVersion != current.version) {
            throw ConflictException("Station '${current.name}' was changed by someone else. Reload and try again.")
        }
        val station = list.rename(command.stationId, command.name)
        stations.saveAll(listOf(station))
        return station
    }

    fun shift(venueId: VenueId, stationId: StationId, delta: Int) {
        lockVenue(venueId)
        stations.saveAll(stations.load(venueId).shift(stationId, delta))
    }

    fun delete(venueId: VenueId, stationId: StationId) {
        lockVenue(venueId)
        val list = stations.load(venueId)
        val station = list[stationId]
        val usedByCategory = categories.loadTree(venueId).categories.any { it.stationId == stationId }
        if (usedByCategory || items.existsWithStation(stationId) || orders.existsAtStation(stationId)) {
            throw ConflictException("Station '${station.name}' is used by categories, menu items or orders. Move them to another station first.")
        }
        val renumbered = list.remove(stationId)
        stations.delete(station)
        stations.saveAll(renumbered)
    }

    private fun lockVenue(id: VenueId): Venue =
        venues.findByIdAndLock(id) ?: throw NotFoundException("Venue", id)
}
