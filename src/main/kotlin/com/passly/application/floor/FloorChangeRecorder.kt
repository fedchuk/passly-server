package com.passly.application.floor

import com.passly.domain.venue.VenueId

interface FloorChangeRecorder {
    fun recordChange(venueId: VenueId)
}
