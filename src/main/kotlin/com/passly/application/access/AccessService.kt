package com.passly.application.access

import com.passly.domain.role.RoleRepository
import com.passly.domain.role.VenueAccess
import com.passly.domain.user.Permission
import com.passly.domain.user.User
import com.passly.domain.user.UserId
import com.passly.domain.user.UserRepository
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AccessService(
    private val users: UserRepository,
    private val venues: VenueRepository,
    private val roles: RoleRepository,
) {

    fun isAllowed(userId: UserId, venueId: VenueId, permission: Permission): Boolean {
        val venue = venues.findById(venueId) ?: return false
        val user = users.findById(userId) ?: return false
        return accessOf(user, venue)?.allows(permission) == true
    }

    fun accessTo(userId: UserId, venueId: VenueId): VenueAccess? {
        val venue = venues.findById(venueId) ?: return null
        val user = users.findById(userId) ?: return null
        return accessOf(user, venue)
    }

    fun venuesOf(userId: UserId): List<AccessibleVenue> {
        val user = users.findById(userId)?.takeIf { it.enabled } ?: return emptyList()
        val owned = user.memberships.filter { it.isOwner }.flatMap { venues.findAllByTenant(it.tenantId) }
        val joined = venues.findAllByIds(user.memberships.mapNotNull { it.venueId })
        return (owned + joined)
            .distinctBy { it.id }
            .mapNotNull { venue -> accessOf(user, venue)?.let { AccessibleVenue(venue.id, venue.name, it) } }
            .sortedBy { it.name.lowercase() }
    }

    private fun accessOf(user: User, venue: Venue): VenueAccess? = when {
        !user.enabled -> null
        user.owns(venue.tenantId) -> VenueAccess.OWNER
        else -> VenueAccess.of(user, venue, roles.load(venue.id))
    }
}
