package com.passly.data.venue

import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId

fun VenueEntity.toDomain() = Venue(
    id = VenueId(id),
    tenantId = TenantId(tenantId),
    name = name,
)
