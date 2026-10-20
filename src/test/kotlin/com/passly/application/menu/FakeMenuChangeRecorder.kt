package com.passly.application.menu

import com.passly.domain.venue.VenueId

class FakeMenuChangeRecorder : MenuChangeRecorder {
    val recorded = mutableListOf<Pair<VenueId, MenuChangeKind>>()

    override fun recordChange(venueId: VenueId, kind: MenuChangeKind) {
        recorded += venueId to kind
    }
}
