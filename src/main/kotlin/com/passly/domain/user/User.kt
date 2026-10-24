package com.passly.domain.user

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId

class User private constructor(
    val id: UserId,
    val email: Email,
    displayName: String,
    passwordHash: String,
    val platformAdmin: Boolean,
    enabled: Boolean,
    memberships: List<Membership>,
    val version: Long?,
) {
    var displayName: String = displayName
        private set

    var passwordHash: String = passwordHash
        private set

    var enabled: Boolean = enabled
        private set

    var memberships: List<Membership> = memberships
        private set

    val isNew: Boolean get() = version == null

    fun roleIn(venue: Venue): Role? {
        if (!enabled) return null
        if (memberships.any { it.isOwner && it.tenantId == venue.tenantId }) return Role.OWNER
        return memberships.firstOrNull { it.venueId == venue.id }?.role
    }

    fun can(permission: Permission, venue: Venue): Boolean =
        roleIn(venue)?.permissions?.contains(permission) == true

    fun joinVenue(venue: Venue, role: Role) {
        requireTenantUser()
        if (role == Role.OWNER) throw ValidationException("Owner is granted for the whole business, not for one venue")
        if (memberships.any { it.venueId == venue.id }) throw ConflictException("$email already works at ${venue.name}")
        if (memberships.size >= MAX_MEMBERSHIPS) throw ValidationException("A user can have at most $MAX_MEMBERSHIPS memberships")
        memberships = memberships + Membership(venue.tenantId, venue.id, role)
    }

    fun changeRole(venue: Venue, role: Role) {
        if (role == Role.OWNER) throw ValidationException("Owner is granted for the whole business, not for one venue")
        val current = membershipIn(venue.id)
        memberships = memberships.map { if (it == current) it.copy(role = role) else it }
    }

    fun leaveVenue(venueId: VenueId) {
        val current = membershipIn(venueId)
        memberships = memberships - current
    }

    fun becomeOwnerOf(tenantId: TenantId) {
        requireTenantUser()
        if (memberships.any { it.isOwner && it.tenantId == tenantId }) throw ConflictException("$email already owns this business")
        memberships = memberships + Membership(tenantId, null, Role.OWNER)
    }

    fun rename(newName: String) {
        displayName = validName(newName)
    }

    fun changePasswordHash(newHash: String) {
        passwordHash = newHash
    }

    fun disable() {
        enabled = false
    }

    private fun membershipIn(venueId: VenueId): Membership =
        memberships.firstOrNull { it.venueId == venueId } ?: throw NotFoundException("Staff member", id)

    private fun requireTenantUser() {
        if (platformAdmin) throw ValidationException("Platform admins do not work at venues")
    }

    override fun equals(other: Any?) = other is User && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "User($id, $email, ${memberships.size} memberships)"

    companion object {
        const val MAX_NAME_LENGTH = 120
        const val MAX_MEMBERSHIPS = 50

        fun create(email: Email, displayName: String, passwordHash: String) =
            User(UserId.new(), email, validName(displayName), passwordHash, false, true, emptyList(), null)

        fun createPlatformAdmin(email: Email, displayName: String, passwordHash: String) =
            User(UserId.new(), email, validName(displayName), passwordHash, true, true, emptyList(), null)

        fun fromStorage(
            id: UserId,
            email: Email,
            displayName: String,
            passwordHash: String,
            platformAdmin: Boolean,
            enabled: Boolean,
            memberships: List<Membership>,
            version: Long,
        ) = User(id, email, displayName, passwordHash, platformAdmin, enabled, memberships, version)

        private fun validName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) throw ValidationException("Name must not be blank")
            if (trimmed.length > MAX_NAME_LENGTH) throw ValidationException("Name is too long (max $MAX_NAME_LENGTH)")
            return trimmed
        }
    }
}
