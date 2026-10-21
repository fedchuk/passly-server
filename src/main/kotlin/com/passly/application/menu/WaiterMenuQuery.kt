package com.passly.application.menu

import com.passly.domain.venue.VenueId

interface WaiterMenuQuery {
    fun menuVersion(venueId: VenueId): Long?
    fun menu(venueId: VenueId): WaiterMenu?
}
