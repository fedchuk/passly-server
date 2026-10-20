package com.passly.application.menu

import com.passly.domain.menu.Station
import com.passly.domain.menu.StationRepository
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class StationService(private val stations: StationRepository) {

    fun list(venueId: VenueId): List<Station> = stations.findAllInVenue(venueId)
}
