package com.passly.domain.role

import com.passly.domain.common.ValidationException
import com.passly.domain.user.Permission
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.validName

class VenueRole private constructor(
    val id: VenueRoleId,
    val tenantId: TenantId,
    val venueId: VenueId,
    name: String,
    permissions: Set<Permission>,
    sortOrder: Int,
    val version: Long?,
) {
    var name: String = name
        internal set

    var permissions: Set<Permission> = permissions
        internal set

    var sortOrder: Int = sortOrder
        internal set

    val isNew: Boolean get() = version == null

    fun allows(permission: Permission): Boolean = permission in permissions

    override fun equals(other: Any?) = other is VenueRole && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "VenueRole($id, '$name', $permissions)"

    companion object {
        const val MAX_NAME_LENGTH = 60
        const val OWNER_NAME = "Owner"

        internal fun new(tenantId: TenantId, venueId: VenueId, name: String, permissions: Set<Permission>, sortOrder: Int) =
            VenueRole(VenueRoleId.new(), tenantId, venueId, validRoleName(name), validPermissions(permissions), sortOrder, null)

        fun fromStorage(
            id: VenueRoleId,
            tenantId: TenantId,
            venueId: VenueId,
            name: String,
            permissions: Set<Permission>,
            sortOrder: Int,
            version: Long,
        ) = VenueRole(id, tenantId, venueId, name, permissions, sortOrder, version)

        internal fun validRoleName(name: String): String {
            val valid = validName(name, "Role", MAX_NAME_LENGTH)
            if (valid.equals(OWNER_NAME, ignoreCase = true)) {
                throw ValidationException("'$OWNER_NAME' is reserved for the owners of the business", "name")
            }
            return valid
        }

        internal fun validPermissions(permissions: Set<Permission>): Set<Permission> {
            permissions.firstOrNull { it in Permission.OWNER_ONLY }?.let {
                throw ValidationException("Only owners can have $it", "permissions")
            }
            return permissions + Permission.EVERY_ROLE
        }
    }
}
