package com.passly.domain.menu

import com.passly.domain.venue.VenueId

interface StationRepository {
    fun existsInVenue(stationId: StationId, venueId: VenueId): Boolean
    fun findAllInVenue(venueId: VenueId): List<Station>
}
