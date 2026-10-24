package com.passly.application.menu

import com.passly.domain.menu.StationId
import com.passly.domain.venue.VenueId

data class CreateStationCommand(
    val venueId: VenueId,
    val name: String,
)

data class RenameStationCommand(
    val venueId: VenueId,
    val stationId: StationId,
    val name: String,
    val expectedVersion: Long? = null,
)
