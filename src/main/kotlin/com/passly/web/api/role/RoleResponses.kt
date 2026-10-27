package com.passly.web.api.role

import com.passly.application.role.RoleSummary
import com.passly.domain.user.Permission
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

data class RoleResponse(
    val id: UUID,
    @Schema(example = "Waiter")
    val name: String,
    val permissions: List<Permission>,
    @Schema(description = "How many people of the venue have this role")
    val members: Int,
    val version: Long,
)

fun RoleSummary.toResponse() = RoleResponse(
    id = role.id.value,
    name = role.name,
    permissions = role.permissions.sortedBy { it.ordinal },
    members = members,
    version = checkNotNull(role.version) { "Role ${role.id} is not stored yet" },
)
