package com.passly.data.user

import com.passly.data.common.upsert
import com.passly.domain.user.Email
import com.passly.domain.user.User
import com.passly.domain.user.UserId
import com.passly.domain.user.UserRepository
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Repository

@Repository
class UserRepositoryImpl(
    private val jpa: UserJpaRepository,
) : UserRepository {

    override fun findById(id: UserId): User? =
        jpa.findWithMembershipsById(id.value)?.toDomain()

    override fun findByEmail(email: Email): User? =
        jpa.findByEmail(email.value)?.toDomain()

    override fun findAllInVenue(venueId: VenueId): List<User> =
        jpa.findAllByVenueId(venueId.value).map { it.toDomain() }

    override fun save(user: User) {
        jpa.upsert(user.id.value, user.version, create = { user.toNewEntity() }, update = { updateFrom(user) })
    }
}
