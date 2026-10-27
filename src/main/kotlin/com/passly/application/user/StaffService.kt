package com.passly.application.user

import com.passly.domain.common.ConflictException
import com.passly.domain.common.ForbiddenException
import com.passly.domain.common.NotFoundException
import com.passly.domain.role.*
import com.passly.domain.user.PasswordHasher
import com.passly.domain.user.User
import com.passly.domain.user.UserId
import com.passly.domain.user.UserRepository
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class StaffService(
    private val venues: VenueRepository,
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val passwordHasher: PasswordHasher,
) {

    @Transactional(readOnly = true)
    fun list(venueId: VenueId): List<StaffMember> {
        val venue = requireVenue(venueId)
        val venueRoles = roles.load(venue.id)
        return users.findAllInVenue(venue.id)
            .mapNotNull { user -> user.roleIdIn(venue.id)?.let { user.toStaffMember(venueRoles[it]) } }
            .sortedBy { it.displayName.lowercase() }
    }

    fun hire(command: HireStaffCommand): StaffMember {
        val venue = requireVenue(command.venueId)
        val role = assignableRole(venue, command.roleId, command.hiredBy)
        if (users.findByEmail(command.email) != null) {
            throw ConflictException("A user with email ${command.email} already exists")
        }
        val user = User.create(command.email, command.displayName, passwordHasher.hash(command.password))
        user.joinVenue(venue, role.id)
        users.save(user)
        return user.toStaffMember(role)
    }

    fun changeRole(command: ChangeStaffRoleCommand): StaffMember {
        val venue = requireVenue(command.venueId)
        if (command.userId == command.changedBy) throw ForbiddenException("You cannot change your own role")
        val user = requireStaff(venue, command.userId)
        val role = assignableRole(venue, command.roleId, command.changedBy)
        user.changeRole(venue, role.id)
        users.save(user)
        return user.toStaffMember(role)
    }

    fun dismiss(venueId: VenueId, userId: UserId) {
        val venue = requireVenue(venueId)
        val user = requireStaff(venue, userId)
        user.leaveVenue(venue.id)
        users.save(user)
    }

    private fun assignableRole(venue: Venue, roleId: VenueRoleId, actorId: UserId?): VenueRole {
        val venueRoles = roles.load(venue.id)
        val role = venueRoles[roleId]
        if (actorId != null && accessOf(actorId, venue, venueRoles)?.covers(role) != true) {
            throw ForbiddenException("You cannot give the role '${role.name}': it allows more than your own role")
        }
        return role
    }

    private fun accessOf(userId: UserId, venue: Venue, venueRoles: RoleList): VenueAccess? =
        users.findById(userId)?.let { VenueAccess.of(it, venue, venueRoles) }

    private fun User.toStaffMember(role: VenueRole) = StaffMember(id, email, displayName, role.id, role.name)

    private fun requireVenue(id: VenueId): Venue =
        venues.findById(id) ?: throw NotFoundException("Venue", id)

    private fun requireStaff(venue: Venue, userId: UserId): User =
        users.findById(userId)?.takeIf { user -> user.memberships.any { it.venueId == venue.id } }
            ?: throw NotFoundException("Staff member", userId)
}
