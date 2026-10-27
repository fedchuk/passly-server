package com.passly.data.role

import com.passly.domain.role.VenueRole
import com.passly.domain.role.VenueRoleId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

fun VenueRoleEntity.toDomain() = VenueRole.fromStorage(
    id = VenueRoleId(id),
    tenantId = TenantId(tenantId),
    venueId = VenueId(venueId),
    name = name,
    permissions = permissions.toSet(),
    sortOrder = sortOrder,
    version = checkNotNull(version) { "Stored role $id has no version" },
)

fun VenueRole.toNewEntity() = VenueRoleEntity(
    id = id.value,
    tenantId = tenantId.value,
    venueId = venueId.value,
    name = name,
    sortOrder = sortOrder,
).also { it.permissions = permissions.toMutableSet() }

fun VenueRoleEntity.updateFrom(role: VenueRole) {
    name = role.name
    sortOrder = role.sortOrder
    if (permissions != role.permissions) permissions = role.permissions.toMutableSet()
}
