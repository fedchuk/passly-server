package com.passly.domain.user

import com.passly.domain.role.VenueRoleId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

data class Membership(
    val tenantId: TenantId,
    val venueId: VenueId?,
    val roleId: VenueRoleId?,
) {
    init {
        require((venueId == null) == (roleId == null)) { "A venue membership needs a role, an owner membership has none" }
    }

    val isOwner: Boolean get() = venueId == null
}
