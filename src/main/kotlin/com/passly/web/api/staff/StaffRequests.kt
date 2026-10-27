package com.passly.web.api.staff

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.*

private const val ROLE_DESCRIPTION =
    "Role of the venue from GET …/roles. It may not allow more than the role of the person who gives it."

data class HireStaffRequest(
    @NotBlank @Size(max = 254) val email: String,
    @NotBlank @Size(max = 120) val displayName: String,
    @Schema(format = "password", description = "At least 8 characters")
    @NotBlank @Size(max = 200) val password: String,
    @Schema(description = ROLE_DESCRIPTION)
    val roleId: UUID,
) {
    override fun toString() = "HireStaffRequest(email=$email, displayName=$displayName, password=***, roleId=$roleId)"
}

data class ChangeStaffRoleRequest(
    @Schema(description = ROLE_DESCRIPTION)
    val roleId: UUID,
)
