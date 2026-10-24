package com.passly.data.menu

import com.passly.data.common.upsert
import com.passly.domain.menu.Station
import com.passly.domain.menu.StationId
import com.passly.domain.menu.StationList
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
        jpa.findAllByVenueIdOrderBySortOrderAscNameAsc(venueId.value).map { it.toDomain() }

    override fun load(venueId: VenueId): StationList = StationList(venueId, findAllInVenue(venueId))

    override fun saveAll(stations: Collection<Station>) {
        stations.forEach { station ->
            jpa.upsert(station.id.value, station.version, create = { station.toNewEntity() }, update = { updateFrom(station) })
        }
    }

    override fun delete(station: Station) {
        jpa.deleteById(station.id.value)
    }
}
