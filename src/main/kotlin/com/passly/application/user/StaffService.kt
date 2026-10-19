package com.passly.application.user

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
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
    private val passwordHasher: PasswordHasher,
) {

    @Transactional(readOnly = true)
    fun list(venueId: VenueId): List<StaffMember> {
        val venue = requireVenue(venueId)
        return users.findAllInVenue(venue.id)
            .mapNotNull { user -> user.roleIn(venue)?.let { StaffMember(user.id, user.email, user.displayName, it) } }
            .sortedBy { it.displayName.lowercase() }
    }

    fun hire(command: HireStaffCommand): User {
        val venue = requireVenue(command.venueId)
        if (users.findByEmail(command.email) != null) {
            throw ConflictException("A user with email ${command.email} already exists")
        }
        val user = User.create(command.email, command.displayName, passwordHasher.hash(command.password))
        user.joinVenue(venue, command.role)
        users.save(user)
        return user
    }

    fun changeRole(command: ChangeStaffRoleCommand): User {
        val venue = requireVenue(command.venueId)
        val user = requireStaff(venue, command.userId)
        user.changeRole(venue, command.role)
        users.save(user)
        return user
    }

    fun dismiss(venueId: VenueId, userId: UserId) {
        val venue = requireVenue(venueId)
        val user = requireStaff(venue, userId)
        user.leaveVenue(venue.id)
        users.save(user)
    }

    private fun requireVenue(id: VenueId): Venue =
        venues.findById(id) ?: throw NotFoundException("Venue", id)

    private fun requireStaff(venue: Venue, userId: UserId): User =
        users.findById(userId)?.takeIf { user -> user.memberships.any { it.venueId == venue.id } }
            ?: throw NotFoundException("Staff member", userId)
}
