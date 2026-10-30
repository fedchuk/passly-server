package com.passly.application.kitchen

import com.passly.domain.menu.StationId
import com.passly.domain.venue.VenueId
import java.time.Instant

interface StationBoardQuery {
    fun ticketsOf(venueId: VenueId, stationId: StationId, since: Instant): List<StationTicket>
}
