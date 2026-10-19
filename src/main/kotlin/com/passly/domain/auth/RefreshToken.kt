package com.passly.domain.auth

import com.passly.domain.user.UserId
import java.time.Instant

class RefreshToken private constructor(
    val id: RefreshTokenId,
    val userId: UserId,
    val familyId: TokenFamilyId,
    val tokenHash: String,
    val expiresAt: Instant,
    revokedAt: Instant?,
    val version: Long?,
) {
    var revokedAt: Instant? = revokedAt
        private set

    val isRevoked: Boolean get() = revokedAt != null

    fun isActiveAt(now: Instant): Boolean = !isRevoked && now.isBefore(expiresAt)

    fun revoke(now: Instant) {
        if (revokedAt == null) revokedAt = now
    }

    override fun equals(other: Any?) = other is RefreshToken && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "RefreshToken($id, user=$userId, family=$familyId, revoked=$isRevoked)"

    companion object {
        fun issue(userId: UserId, familyId: TokenFamilyId, tokenHash: String, expiresAt: Instant) =
            RefreshToken(RefreshTokenId.new(), userId, familyId, tokenHash, expiresAt, null, null)

        fun fromStorage(
            id: RefreshTokenId,
            userId: UserId,
            familyId: TokenFamilyId,
            tokenHash: String,
            expiresAt: Instant,
            revokedAt: Instant?,
            version: Long,
        ) = RefreshToken(id, userId, familyId, tokenHash, expiresAt, revokedAt, version)
    }
}
