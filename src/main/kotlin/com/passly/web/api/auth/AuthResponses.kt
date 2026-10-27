package com.passly.web.api.auth

import com.passly.application.access.AccessibleVenue
import com.passly.application.auth.AuthTokens
import com.passly.domain.user.Permission
import com.passly.domain.user.User
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.*

data class TokenResponse(
    val tokenType: String,
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)

data class MeResponse(
    val id: UUID,
    val email: String,
    val displayName: String,
    val platformAdmin: Boolean,
    val memberships: List<MembershipResponse>,
    @Schema(description = "Venues the user can open, with the role there and what it allows")
    val venues: List<VenueAccessResponse>,
)

data class MembershipResponse(
    val tenantId: UUID,
    @Schema(description = "Empty for an owner of the whole business")
    val venueId: UUID?,
    @Schema(description = "Empty for an owner of the whole business")
    val roleId: UUID?,
    val owner: Boolean,
)

data class VenueAccessResponse(
    val venueId: UUID,
    val name: String,
    val owner: Boolean,
    @Schema(example = "Waiter")
    val role: String,
    val permissions: List<Permission>,
)

fun AuthTokens.toResponse() = TokenResponse(
    tokenType = "Bearer",
    accessToken = accessToken,
    accessTokenExpiresAt = accessTokenExpiresAt,
    refreshToken = refreshToken,
    refreshTokenExpiresAt = refreshTokenExpiresAt,
)

fun User.toMeResponse(venues: List<AccessibleVenue>) = MeResponse(
    id = id.value,
    email = email.value,
    displayName = displayName,
    platformAdmin = platformAdmin,
    memberships = memberships.map { MembershipResponse(it.tenantId.value, it.venueId?.value, it.roleId?.value, it.isOwner) },
    venues = venues.map { it.toResponse() },
)

fun AccessibleVenue.toResponse() = VenueAccessResponse(
    venueId = venueId.value,
    name = name,
    owner = access.owner,
    role = access.roleName,
    permissions = access.permissions.sortedBy { it.ordinal },
)
