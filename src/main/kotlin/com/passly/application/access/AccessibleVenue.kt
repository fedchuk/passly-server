package com.passly.application.access

import com.passly.domain.role.VenueAccess
import com.passly.domain.venue.VenueId

data class AccessibleVenue(
    val venueId: VenueId,
    val name: String,
    val access: VenueAccess,
)
