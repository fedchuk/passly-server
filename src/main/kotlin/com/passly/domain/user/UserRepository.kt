package com.passly.domain.user

import com.passly.domain.venue.VenueId

interface UserRepository {
    fun findById(id: UserId): User?
    fun findByEmail(email: Email): User?
    fun findAllInVenue(venueId: VenueId): List<User>
    fun save(user: User)
}
