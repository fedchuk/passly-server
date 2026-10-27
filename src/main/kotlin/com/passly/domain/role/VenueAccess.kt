package com.passly.domain.role

import com.passly.domain.user.Permission
import com.passly.domain.user.User
import com.passly.domain.venue.Venue

data class VenueAccess(
    val owner: Boolean,
    val roleId: VenueRoleId?,
    val roleName: String,
    val permissions: Set<Permission>,
) {
    fun allows(permission: Permission): Boolean = permission in permissions

    fun covers(role: VenueRole): Boolean = owner || permissions.containsAll(role.permissions)

    companion object {
        val OWNER = VenueAccess(true, null, VenueRole.OWNER_NAME, Permission.entries.toSet())

        fun of(user: User, venue: Venue, roles: RoleList): VenueAccess? {
            if (!user.enabled) return null
            if (user.owns(venue.tenantId)) return OWNER
            val roleId = user.roleIdIn(venue.id) ?: return null
            val role = roles[roleId]
            return VenueAccess(false, role.id, role.name, role.permissions)
        }
    }
}
