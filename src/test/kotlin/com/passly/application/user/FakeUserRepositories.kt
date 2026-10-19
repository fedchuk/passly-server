package com.passly.application.user

import com.passly.domain.user.Email
import com.passly.domain.user.PasswordHasher
import com.passly.domain.user.RawPassword
import com.passly.domain.user.User
import com.passly.domain.user.UserId
import com.passly.domain.user.UserRepository
import com.passly.domain.venue.VenueId

class FakeUserRepository : UserRepository {
    val stored = linkedMapOf<UserId, User>()

    override fun findById(id: UserId) = stored[id]?.let(::copyOf)

    override fun findByEmail(email: Email) = stored.values.firstOrNull { it.email == email }?.let(::copyOf)

    override fun findAllInVenue(venueId: VenueId) =
        stored.values.filter { user -> user.memberships.any { it.venueId == venueId } }.map(::copyOf)

    override fun save(user: User) {
        stored[user.id] = copyOf(user)
    }

    private fun copyOf(u: User) = User.fromStorage(
        u.id, u.email, u.displayName, u.passwordHash, u.platformAdmin, u.enabled, u.memberships, u.version ?: 0,
    )
}

class FakePasswordHasher : PasswordHasher {
    override fun hash(password: RawPassword) = "hashed:${password.value}"
    override fun matches(password: String, hash: String) = hash == "hashed:$password"
}
