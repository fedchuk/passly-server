package com.passly.web.api.role

import com.passly.application.role.CreateRoleCommand
import com.passly.application.role.UpdateRoleCommand
import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Permission
import com.passly.domain.venue.VenueId
import com.passly.web.api.VERSION_DESCRIPTION
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.*

private const val PERMISSIONS_DESCRIPTION =
    "What the role may do. VIEW_MENU is always added. MANAGE_ROLES belongs to owners only and is refused."

data class CreateRoleRequest(
    @NotBlank @Size(max = 60) val name: String,
    @Schema(description = PERMISSIONS_DESCRIPTION)
    val permissions: Set<Permission> = emptySet(),
    @Schema(description = "Copy the permissions of this role instead of the list above")
    val copyOf: UUID? = null,
) {
    fun toCommand(venueId: VenueId) = CreateRoleCommand(venueId, name, permissions, copyOf?.let(::VenueRoleId))
}

data class UpdateRoleRequest(
    @NotBlank @Size(max = 60) val name: String,
    @Schema(description = PERMISSIONS_DESCRIPTION)
    val permissions: Set<Permission>,
    @Schema(description = VERSION_DESCRIPTION, example = "0")
    val version: Long,
) {
    fun toCommand(venueId: VenueId, roleId: VenueRoleId) =
        UpdateRoleCommand(venueId, roleId, name, permissions, version)
}
