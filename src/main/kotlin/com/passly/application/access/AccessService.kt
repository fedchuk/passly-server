package com.passly.application.access

import com.passly.domain.user.Permission
import com.passly.domain.user.UserId
import com.passly.domain.user.UserRepository
import com.passly.domain.venue.VenueId
import com.passly.domain.venue.VenueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AccessService(
    private val users: UserRepository,
    private val venues: VenueRepository,
) {

    fun isAllowed(userId: UserId, venueId: VenueId, permission: Permission): Boolean {
        val venue = venues.findById(venueId) ?: return false
        val user = users.findById(userId) ?: return false
        return user.can(permission, venue)
    }

    fun venuesOf(userId: UserId): List<AccessibleVenue> {
        val user = users.findById(userId)?.takeIf { it.enabled } ?: return emptyList()
        val owned = user.memberships.filter { it.isOwner }.flatMap { venues.findAllByTenant(it.tenantId) }
        val joined = venues.findAllByIds(user.memberships.mapNotNull { it.venueId })
        return (owned + joined)
            .distinctBy { it.id }
            .mapNotNull { venue -> user.roleIn(venue)?.let { AccessibleVenue(venue.id, venue.name, it) } }
            .sortedBy { it.name.lowercase() }
    }
}
