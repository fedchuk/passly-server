package com.passly.web.admin

import com.passly.application.user.StaffMember
import java.util.*

data class StaffRowView(
    val userId: UUID,
    val name: String,
    val email: String,
    val roleId: UUID,
    val roleName: String,
    val you: Boolean,
    val changeable: Boolean,
)

fun StaffMember.toRowView(currentUserId: UUID, assignableRoleIds: Set<UUID>) = StaffRowView(
    userId = userId.value,
    name = displayName,
    email = email.value,
    roleId = roleId.value,
    roleName = roleName,
    you = userId.value == currentUserId,
    changeable = userId.value != currentUserId && roleId.value in assignableRoleIds,
)
