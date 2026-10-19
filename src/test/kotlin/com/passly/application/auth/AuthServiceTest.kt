package com.passly.application.auth

import com.passly.application.user.FakePasswordHasher
import com.passly.application.user.FakeUserRepository
import com.passly.domain.auth.TokenSecrets
import com.passly.domain.common.UnauthorizedException
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.User
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthServiceTest {

    private val properties =
        AuthProperties(accessTokenTtl = Duration.ofMinutes(15), refreshTokenTtl = Duration.ofDays(30))
    private val clock = MutableClock(Instant.parse("2026-10-20T10:00:00Z"))
    private val users = FakeUserRepository()
    private val refreshTokens = FakeRefreshTokenRepository()
    private val hasher = FakePasswordHasher()
    private val service =
        AuthService(users, refreshTokens, hasher, FakeAccessTokenIssuer(properties.accessTokenTtl), properties, clock)

    private val anna = User.create(Email.of("anna@bistro.es"), "Anna", hasher.hash(RawPassword.of("password-1")))
        .also(users::save)

    private fun login(email: String = "anna@bistro.es", password: String = "password-1") =
        service.login(LoginCommand(Email.of(email), password))

    @Test
    fun `login issues both tokens and stores only the hash of the refresh token`() {
        val tokens = login()

        assertEquals("access-for-${anna.id}", tokens.accessToken)
        assertEquals(clock.instant() + Duration.ofMinutes(15), tokens.accessTokenExpiresAt)
        assertEquals(clock.instant() + Duration.ofDays(30), tokens.refreshTokenExpiresAt)
        val stored = refreshTokens.stored.values.single()
        assertEquals(TokenSecrets.hash(tokens.refreshToken), stored.tokenHash)
        assertTrue(refreshTokens.stored.values.none { it.tokenHash == tokens.refreshToken })
    }

    @Test
    fun `wrong password and unknown email give the same answer`() {
        val wrongPassword = assertFailsWith<UnauthorizedException> { login(password = "wrong-pass") }
        val unknownEmail = assertFailsWith<UnauthorizedException> { login(email = "nobody@bistro.es") }

        assertEquals(wrongPassword.message, unknownEmail.message)
    }

    @Test
    fun `disabled user cannot log in`() {
        users.save(users.findById(anna.id)!!.apply { disable() })

        assertFailsWith<UnauthorizedException> { login() }
    }

    @Test
    fun `refresh rotates the token`() {
        val first = login()

        val second = service.refresh(first.refreshToken)

        assertTrue(second.refreshToken != first.refreshToken)
        service.refresh(second.refreshToken)
    }

    @Test
    fun `reusing an old refresh token kills the whole session`() {
        val first = login()
        val second = service.refresh(first.refreshToken)

        assertFailsWith<UnauthorizedException> { service.refresh(first.refreshToken) }
        assertFailsWith<UnauthorizedException> { service.refresh(second.refreshToken) }
    }

    @Test
    fun `reuse in one session does not touch other devices`() {
        val phone = login()
        val tablet = login()
        service.refresh(phone.refreshToken)

        assertFailsWith<UnauthorizedException> { service.refresh(phone.refreshToken) }
        service.refresh(tablet.refreshToken)
    }

    @Test
    fun `expired refresh token is rejected`() {
        val tokens = login()
        clock.advance(Duration.ofDays(30))

        assertFailsWith<UnauthorizedException> { service.refresh(tokens.refreshToken) }
    }

    @Test
    fun `logout ends the session and logout everywhere ends all of them`() {
        val phone = login()
        val tablet = login()
        val laptop = login()

        service.logout(phone.refreshToken)
        assertFailsWith<UnauthorizedException> { service.refresh(phone.refreshToken) }
        service.refresh(tablet.refreshToken)

        service.logoutEverywhere(anna.id)
        assertFailsWith<UnauthorizedException> { service.refresh(laptop.refreshToken) }
    }

    @Test
    fun `unknown refresh token is rejected and logout with it is ignored`() {
        assertFailsWith<UnauthorizedException> { service.refresh("made-up") }
        service.logout("made-up")
    }
}
