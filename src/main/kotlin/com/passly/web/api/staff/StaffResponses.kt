package com.passly.web.api.staff

import com.passly.application.user.StaffMember
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

data class StaffMemberResponse(
    val userId: UUID,
    val email: String,
    val displayName: String,
    val roleId: UUID,
    @Schema(example = "Waiter")
    val roleName: String,
)

fun StaffMember.toResponse() = StaffMemberResponse(userId.value, email.value, displayName, roleId.value, roleName)
