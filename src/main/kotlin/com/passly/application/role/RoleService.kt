package com.passly.application.role

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.role.RoleList
import com.passly.domain.role.RoleRepository
import com.passly.domain.role.VenueRole
import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Permission
import com.passly.domain.user.UserRepository
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class RoleService(
    private val venues: VenueRepository,
    private val roles: RoleRepository,
    private val users: UserRepository,
) {

    @Transactional(readOnly = true)
    fun list(venueId: VenueId): List<RoleSummary> {
        requireVenue(venueId)
        val members = membersByRole(venueId)
        return roles.load(venueId).roles.map { RoleSummary(it, members[it.id] ?: 0) }
    }

    @Transactional(readOnly = true)
    fun get(venueId: VenueId, roleId: VenueRoleId): RoleSummary {
        requireVenue(venueId)
        return RoleSummary(roles.load(venueId)[roleId], membersByRole(venueId)[roleId] ?: 0)
    }

    fun create(command: CreateRoleCommand): VenueRole {
        val venue = lockVenue(command.venueId)
        val list = roles.load(venue.id)
        val permissions = command.copyOf?.let { list[it].permissions } ?: command.permissions
        val role = list.add(venue.tenantId, command.name, permissions)
        roles.saveAll(listOf(role))
        return role
    }

    fun update(command: UpdateRoleCommand): VenueRole {
        lockVenue(command.venueId)
        val list = roles.load(command.venueId)
        requireVersion(list, command.roleId, command.expectedVersion)
        list.rename(command.roleId, command.name)
        val role = list.grant(command.roleId, command.permissions)
        roles.saveAll(listOf(role))
        return role
    }

    fun rename(command: RenameRoleCommand): VenueRole {
        lockVenue(command.venueId)
        val role = roles.load(command.venueId).rename(command.roleId, command.name)
        roles.saveAll(listOf(role))
        return role
    }

    fun grant(command: GrantPermissionsCommand): List<VenueRole> {
        lockVenue(command.venueId)
        val list = roles.load(command.venueId)
        command.grants.keys.forEach { requireVersion(list, it, command.expectedVersions[it]) }
        val changed = command.grants
            .filter { (roleId, permissions) -> list[roleId].permissions != permissions + Permission.EVERY_ROLE }
            .map { (roleId, permissions) -> list.grant(roleId, permissions) }
        roles.saveAll(changed)
        return changed
    }

    fun delete(venueId: VenueId, roleId: VenueRoleId): VenueRole {
        lockVenue(venueId)
        val list = roles.load(venueId)
        val role = list[roleId]
        val members = membersByRole(venueId)[roleId] ?: 0
        if (members > 0) {
            throw ConflictException("Role '${role.name}' is given to $members ${if (members == 1) "person" else "people"}. Give them another role first.")
        }
        val renumbered = list.remove(roleId)
        roles.delete(role)
        roles.saveAll(renumbered)
        return role
    }

    private fun membersByRole(venueId: VenueId): Map<VenueRoleId, Int> =
        users.findAllInVenue(venueId).mapNotNull { it.roleIdIn(venueId) }.groupingBy { it }.eachCount()

    private fun requireVersion(list: RoleList, roleId: VenueRoleId, expectedVersion: Long?) {
        val role = list[roleId]
        if (expectedVersion != null && expectedVersion != role.version) {
            throw ConflictException("Role '${role.name}' was changed by someone else. Reload and try again.")
        }
    }

    private fun requireVenue(id: VenueId): Venue =
        venues.findById(id) ?: throw NotFoundException("Venue", id)

    private fun lockVenue(id: VenueId): Venue =
        venues.findByIdAndLock(id) ?: throw NotFoundException("Venue", id)
}
