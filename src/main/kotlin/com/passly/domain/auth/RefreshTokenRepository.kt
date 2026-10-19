package com.passly.domain.auth

import com.passly.domain.user.UserId
import java.time.Instant

interface RefreshTokenRepository {
    fun findByHash(tokenHash: String): RefreshToken?
    fun save(token: RefreshToken)
    fun revokeFamily(familyId: TokenFamilyId, now: Instant)
    fun revokeAllOfUser(userId: UserId, now: Instant)
}
