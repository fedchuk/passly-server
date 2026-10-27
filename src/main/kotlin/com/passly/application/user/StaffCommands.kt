package com.passly.application.user

import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId

data class HireStaffCommand(
    val venueId: VenueId,
    val email: Email,
    val displayName: String,
    val password: RawPassword,
    val roleId: VenueRoleId,
    val hiredBy: UserId? = null,
)

data class ChangeStaffRoleCommand(
    val venueId: VenueId,
    val userId: UserId,
    val roleId: VenueRoleId,
    val changedBy: UserId? = null,
)
