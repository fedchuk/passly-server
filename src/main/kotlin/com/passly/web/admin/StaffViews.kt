package com.passly.web.admin

import com.passly.application.user.StaffMember
import com.passly.domain.user.Role
import java.util.*

data class StaffRowView(
    val userId: UUID,
    val name: String,
    val email: String,
    val role: Role,
    val you: Boolean,
)

val ASSIGNABLE_ROLES = listOf(Role.MANAGER, Role.WAITER, Role.KITCHEN)

fun StaffMember.toRowView(currentUserId: UUID) = StaffRowView(
    userId = userId.value,
    name = displayName,
    email = email.value,
    role = role,
    you = userId.value == currentUserId,
)
