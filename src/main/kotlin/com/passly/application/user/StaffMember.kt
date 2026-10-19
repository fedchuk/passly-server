package com.passly.application.user

import com.passly.domain.user.Email
import com.passly.domain.user.Role
import com.passly.domain.user.UserId

data class StaffMember(
    val userId: UserId,
    val email: Email,
    val displayName: String,
    val role: Role,
)
