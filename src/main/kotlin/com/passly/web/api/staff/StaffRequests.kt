package com.passly.web.api.staff

import com.passly.domain.user.Role
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class HireStaffRequest(
    @NotBlank @Size(max = 254) val email: String,
    @NotBlank @Size(max = 120) val displayName: String,
    @Schema(format = "password", description = "At least 8 characters")
    @NotBlank @Size(max = 200) val password: String,
    val role: Role,
) {
    override fun toString() = "HireStaffRequest(email=$email, displayName=$displayName, password=***, role=$role)"
}

data class ChangeStaffRoleRequest(
    val role: Role,
)
