package com.passly.data.menu

import com.passly.domain.menu.Station
import com.passly.domain.menu.StationId
import com.passly.domain.menu.StationRepository
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Repository

@Repository
class StationRepositoryImpl(
    private val jpa: StationJpaRepository,
) : StationRepository {

    override fun existsInVenue(stationId: StationId, venueId: VenueId): Boolean =
        jpa.existsByIdAndVenueId(stationId.value, venueId.value)

    override fun findAllInVenue(venueId: VenueId): List<Station> =
        jpa.findAllByVenueIdOrderBySortOrderAscNameAsc(venueId.value)
            .map { Station(StationId(it.id), VenueId(it.venueId), it.name) }
}
