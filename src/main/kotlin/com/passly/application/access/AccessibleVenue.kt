package com.passly.application.access

import com.passly.domain.user.Role
import com.passly.domain.venue.VenueId

data class AccessibleVenue(
    val venueId: VenueId,
    val name: String,
    val role: Role,
)
