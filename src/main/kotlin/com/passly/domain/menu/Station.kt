package com.passly.domain.menu

import com.passly.domain.venue.VenueId

data class Station(
    val id: StationId,
    val venueId: VenueId,
    val name: String,
)
