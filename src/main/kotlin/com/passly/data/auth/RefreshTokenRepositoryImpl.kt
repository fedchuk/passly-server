package com.passly.data.auth

import com.passly.data.common.upsert
import com.passly.domain.auth.RefreshToken
import com.passly.domain.auth.RefreshTokenRepository
import com.passly.domain.auth.TokenFamilyId
import com.passly.domain.user.UserId
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class RefreshTokenRepositoryImpl(
    private val jpa: RefreshTokenJpaRepository,
) : RefreshTokenRepository {

    override fun findByHash(tokenHash: String): RefreshToken? =
        jpa.findByTokenHash(tokenHash)?.toDomain()

    override fun save(token: RefreshToken) {
        jpa.upsert(token.id.value, token.version, create = { token.toNewEntity() }, update = { revokedAt = token.revokedAt })
    }

    override fun revokeFamily(familyId: TokenFamilyId, now: Instant) {
        jpa.revokeFamily(familyId.value, now)
    }

    override fun revokeAllOfUser(userId: UserId, now: Instant) {
        jpa.revokeAllOfUser(userId.value, now)
    }
}
