package com.passly.application.venue

import com.passly.domain.venue.VenueId

interface VenueOverviewQuery {
    fun overviewOf(venueId: VenueId): VenueOverview
}
