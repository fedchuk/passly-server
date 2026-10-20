package com.passly.web.api.staff

import com.passly.application.user.StaffMember
import com.passly.domain.user.Role
import java.util.UUID

data class StaffMemberResponse(
    val userId: UUID,
    val email: String,
    val displayName: String,
    val role: Role,
)

fun StaffMember.toResponse() = StaffMemberResponse(userId.value, email.value, displayName, role)
