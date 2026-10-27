package com.passly.application.role

import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Permission
import com.passly.domain.venue.VenueId

data class CreateRoleCommand(
    val venueId: VenueId,
    val name: String,
    val permissions: Set<Permission> = emptySet(),
    val copyOf: VenueRoleId? = null,
)

data class UpdateRoleCommand(
    val venueId: VenueId,
    val roleId: VenueRoleId,
    val name: String,
    val permissions: Set<Permission>,
    val expectedVersion: Long? = null,
)

data class RenameRoleCommand(
    val venueId: VenueId,
    val roleId: VenueRoleId,
    val name: String,
)

data class GrantPermissionsCommand(
    val venueId: VenueId,
    val grants: Map<VenueRoleId, Set<Permission>>,
    val expectedVersions: Map<VenueRoleId, Long> = emptyMap(),
)
