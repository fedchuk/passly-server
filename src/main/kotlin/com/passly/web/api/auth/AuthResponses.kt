package com.passly.web.api.auth

import com.passly.application.auth.AuthTokens
import com.passly.domain.user.Role
import com.passly.domain.user.User
import java.time.Instant
import java.util.UUID

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
)

data class MembershipResponse(
    val tenantId: UUID,
    val venueId: UUID?,
    val role: Role,
)

fun AuthTokens.toResponse() = TokenResponse(
    tokenType = "Bearer",
    accessToken = accessToken,
    accessTokenExpiresAt = accessTokenExpiresAt,
    refreshToken = refreshToken,
    refreshTokenExpiresAt = refreshTokenExpiresAt,
)

fun User.toMeResponse() = MeResponse(
    id = id.value,
    email = email.value,
    displayName = displayName,
    platformAdmin = platformAdmin,
    memberships = memberships.map { MembershipResponse(it.tenantId.value, it.venueId?.value, it.role) },
)
