package com.passly.application.auth

import com.passly.application.user.FakePasswordHasher
import com.passly.application.user.FakeUserRepository
import com.passly.domain.auth.RefreshToken
import com.passly.domain.auth.TokenFamilyId
import com.passly.domain.common.ValidationException
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.User
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PasswordServiceTest {

    private val clock = MutableClock(Instant.parse("2026-10-20T10:00:00Z"))
    private val users = FakeUserRepository()
    private val refreshTokens = FakeRefreshTokenRepository()
    private val hasher = FakePasswordHasher()
    private val service = PasswordService(users, refreshTokens, hasher, clock)

    private val max = User.create(Email.of("max@bistro.es"), "Max", hasher.hash(RawPassword.of("old-password")))
        .also { users.save(it) }

    private fun change(current: String, new: String) =
        service.change(ChangePasswordCommand(max.id, current, RawPassword.of(new)))

    @Test
    fun `password is replaced and every mobile session is revoked`() {
        refreshTokens.save(RefreshToken.issue(max.id, TokenFamilyId.new(), "phone", clock.instant() + Duration.ofDays(30)))
        refreshTokens.save(RefreshToken.issue(max.id, TokenFamilyId.new(), "tablet", clock.instant() + Duration.ofDays(30)))

        change("old-password", "new-password")

        assertEquals("hashed:new-password", users.stored.getValue(max.id).passwordHash)
        assertTrue(refreshTokens.stored.values.all { it.isRevoked })
    }

    @Test
    fun `wrong current password is reported on its field`() {
        val error = assertFailsWith<ValidationException> { change("guess-1234", "new-password") }

        assertEquals("currentPassword", error.field)
        assertEquals("hashed:old-password", users.stored.getValue(max.id).passwordHash)
    }

    @Test
    fun `new password must differ from the current one`() {
        val error = assertFailsWith<ValidationException> { change("old-password", "old-password") }

        assertEquals("newPassword", error.field)
    }
}
