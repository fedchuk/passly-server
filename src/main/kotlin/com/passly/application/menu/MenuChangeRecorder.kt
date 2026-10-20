package com.passly.application.menu

import com.passly.domain.venue.VenueId

interface MenuChangeRecorder {
    fun recordChange(venueId: VenueId, kind: MenuChangeKind)
}
