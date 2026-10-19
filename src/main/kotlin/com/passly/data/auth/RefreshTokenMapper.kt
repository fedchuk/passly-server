package com.passly.data.auth

import com.passly.domain.auth.RefreshToken
import com.passly.domain.auth.RefreshTokenId
import com.passly.domain.auth.TokenFamilyId
import com.passly.domain.user.UserId

fun RefreshTokenEntity.toDomain() = RefreshToken.fromStorage(
    id = RefreshTokenId(id),
    userId = UserId(userId),
    familyId = TokenFamilyId(familyId),
    tokenHash = tokenHash,
    expiresAt = expiresAt,
    revokedAt = revokedAt,
    version = checkNotNull(version) { "Stored refresh token $id has no version" },
)

fun RefreshToken.toNewEntity() = RefreshTokenEntity(
    id = id.value,
    userId = userId.value,
    familyId = familyId.value,
    tokenHash = tokenHash,
    expiresAt = expiresAt,
    revokedAt = revokedAt,
)
