package com.passly.data.user

import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Email
import com.passly.domain.user.Membership
import com.passly.domain.user.User
import com.passly.domain.user.UserId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.VenueId

fun UserEntity.toDomain() = User.fromStorage(
    id = UserId(id),
    email = Email.of(email),
    displayName = displayName,
    passwordHash = passwordHash,
    platformAdmin = platformAdmin,
    enabled = enabled,
    memberships = memberships.map { it.toDomain() },
    version = checkNotNull(version) { "Stored user $id has no version" },
)

fun User.toNewEntity() = UserEntity(
    id = id.value,
    email = email.value,
    displayName = displayName,
    passwordHash = passwordHash,
    platformAdmin = platformAdmin,
    enabled = enabled,
).also { it.memberships = memberships.toEmbeddables() }

fun UserEntity.updateFrom(user: User) {
    displayName = user.displayName
    passwordHash = user.passwordHash
    enabled = user.enabled
    if (memberships.map { it.toDomain() } != user.memberships) memberships = user.memberships.toEmbeddables()
}

private fun MembershipEmbeddable.toDomain() = Membership(TenantId(tenantId), venueId?.let(::VenueId), roleId?.let(::VenueRoleId))

private fun List<Membership>.toEmbeddables() =
    map { MembershipEmbeddable(it.tenantId.value, it.venueId?.value, it.roleId?.value) }.toMutableList()
