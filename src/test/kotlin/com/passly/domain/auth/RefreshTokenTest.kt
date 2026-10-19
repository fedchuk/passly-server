package com.passly.domain.auth

import com.passly.domain.user.UserId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshTokenTest {

    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val token = RefreshToken.issue(UserId.new(), TokenFamilyId.new(), "hash", now.plusSeconds(60))

    @Test
    fun `token is active until it expires`() {
        assertTrue(token.isActiveAt(now))
        assertFalse(token.isActiveAt(now.plusSeconds(60)))
    }

    @Test
    fun `revoked token is not active and keeps the first revocation time`() {
        token.revoke(now)
        token.revoke(now.plusSeconds(10))

        assertFalse(token.isActiveAt(now))
        assertEquals(now, token.revokedAt)
    }
}
