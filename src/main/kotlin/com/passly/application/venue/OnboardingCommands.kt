package com.passly.application.venue

import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

data class RegisterBusinessCommand(
    val businessName: String,
    val venueName: String,
    val ownerEmail: Email,
    val ownerName: String,
    val ownerPassword: RawPassword,
)

data class RegisteredBusiness(
    val tenantId: TenantId,
    val venueId: VenueId,
    val ownerId: UserId,
)
