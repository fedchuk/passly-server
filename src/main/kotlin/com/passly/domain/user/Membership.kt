package com.passly.domain.user

import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

data class Membership(
    val tenantId: TenantId,
    val venueId: VenueId?,
    val role: Role,
) {
    init {
        require((role == Role.OWNER) == (venueId == null)) { "Only an owner membership covers the whole tenant" }
    }

    val isOwner: Boolean get() = role == Role.OWNER
}
