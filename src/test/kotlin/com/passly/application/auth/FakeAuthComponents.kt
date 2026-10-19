package com.passly.application.auth

import com.passly.domain.auth.RefreshToken
import com.passly.domain.auth.RefreshTokenId
import com.passly.domain.auth.RefreshTokenRepository
import com.passly.domain.auth.TokenFamilyId
import com.passly.domain.user.User
import com.passly.domain.user.UserId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FakeRefreshTokenRepository : RefreshTokenRepository {
    val stored = linkedMapOf<RefreshTokenId, RefreshToken>()

    override fun findByHash(tokenHash: String) = stored.values.firstOrNull { it.tokenHash == tokenHash }?.let(::copyOf)

    override fun save(token: RefreshToken) {
        stored[token.id] = copyOf(token)
    }

    override fun revokeFamily(familyId: TokenFamilyId, now: Instant) {
        stored.values.filter { it.familyId == familyId }.forEach { it.revoke(now) }
    }

    override fun revokeAllOfUser(userId: UserId, now: Instant) {
        stored.values.filter { it.userId == userId }.forEach { it.revoke(now) }
    }

    private fun copyOf(t: RefreshToken) = RefreshToken.fromStorage(
        t.id, t.userId, t.familyId, t.tokenHash, t.expiresAt, t.revokedAt, t.version ?: 0,
    )
}

class FakeAccessTokenIssuer(private val ttl: Duration) : AccessTokenIssuer {
    override fun issue(user: User, issuedAt: Instant) = IssuedAccessToken("access-for-${user.id}", issuedAt + ttl)
}

class MutableClock(private var now: Instant) : Clock() {
    fun advance(duration: Duration) {
        now += duration
    }

    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}
