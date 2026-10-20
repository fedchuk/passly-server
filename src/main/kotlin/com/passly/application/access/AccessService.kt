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
}
