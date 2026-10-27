package com.passly.application.user

import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Email
import com.passly.domain.user.UserId

data class StaffMember(
    val userId: UserId,
    val email: Email,
    val displayName: String,
    val roleId: VenueRoleId,
    val roleName: String,
)
