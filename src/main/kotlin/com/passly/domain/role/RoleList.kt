package com.passly.domain.role

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.user.Permission
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

class RoleList(
    val venueId: VenueId,
    roles: Collection<VenueRole>,
) {
    private val byId: MutableMap<VenueRoleId, VenueRole> = roles.associateByTo(LinkedHashMap()) { it.id }

    init {
        require(roles.all { it.venueId == venueId }) { "All roles must belong to venue $venueId" }
    }

    val roles: List<VenueRole> get() = byId.values.sortedBy { it.sortOrder }

    operator fun get(id: VenueRoleId): VenueRole = byId[id] ?: throw NotFoundException("Role", id)

    operator fun contains(id: VenueRoleId): Boolean = id in byId

    fun add(tenantId: TenantId, name: String, permissions: Set<Permission>): VenueRole {
        if (byId.size >= MAX_ROLES) throw ValidationException("A venue can have at most $MAX_ROLES roles")
        val validName = VenueRole.validRoleName(name)
        requireFreeName(validName, except = null)
        val role = VenueRole.new(tenantId, venueId, validName, permissions, (byId.values.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        byId[role.id] = role
        return role
    }

    fun addPresets(tenantId: TenantId): List<VenueRole> =
        RolePreset.entries
            .filter { preset -> byId.values.none { it.name.equals(preset.roleName, ignoreCase = true) } }
            .map { add(tenantId, it.roleName, it.permissions) }

    fun rename(id: VenueRoleId, name: String): VenueRole {
        val role = get(id)
        val validName = VenueRole.validRoleName(name)
        requireFreeName(validName, except = id)
        role.name = validName
        return role
    }

    fun grant(id: VenueRoleId, permissions: Set<Permission>): VenueRole {
        val role = get(id)
        role.permissions = VenueRole.validPermissions(permissions)
        return role
    }

    fun remove(id: VenueRoleId): Set<VenueRole> {
        get(id)
        byId.remove(id)
        val changed = linkedSetOf<VenueRole>()
        roles.forEachIndexed { index, role ->
            if (role.sortOrder != index) {
                role.sortOrder = index
                changed += role
            }
        }
        return changed
    }

    private fun requireFreeName(name: String, except: VenueRoleId?) {
        if (byId.values.any { it.id != except && it.name.equals(name, ignoreCase = true) }) {
            throw ConflictException("Role '$name' already exists")
        }
    }

    companion object {
        const val MAX_ROLES = 20
    }
}
