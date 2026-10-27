package com.passly.application.role

import com.passly.domain.role.VenueRole

data class RoleSummary(
    val role: VenueRole,
    val members: Int,
)
