package com.passly.application.auth

import com.passly.domain.auth.RefreshToken
import com.passly.domain.auth.RefreshTokenRepository
import com.passly.domain.auth.TokenFamilyId
import com.passly.domain.auth.TokenSecrets
import com.passly.domain.common.UnauthorizedException
import com.passly.domain.user.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
@Transactional
class AuthService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwordHasher: PasswordHasher,
    private val accessTokens: AccessTokenIssuer,
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    private val dummyHash by lazy { passwordHasher.hash(RawPassword.of("timing-equalizer")) }

    fun login(command: LoginCommand): AuthTokens {
        val user = users.findByEmail(command.email)?.takeIf { it.enabled }
        if (user == null) {
            passwordHasher.matches(command.password, dummyHash)
            throw invalidCredentials()
        }
        if (!passwordHasher.matches(command.password, user.passwordHash)) throw invalidCredentials()
        return issueTokens(user, TokenFamilyId.new())
    }

    @Transactional(noRollbackFor = [UnauthorizedException::class])
    fun refresh(refreshToken: String): AuthTokens {
        val now = clock.instant()
        val token = refreshTokens.findByHash(TokenSecrets.hash(refreshToken)) ?: throw invalidRefreshToken()
        if (token.isRevoked) {
            refreshTokens.revokeFamily(token.familyId, now)
            throw invalidRefreshToken()
        }
        if (!token.isActiveAt(now)) throw invalidRefreshToken()
        val user = users.findById(token.userId)?.takeIf { it.enabled } ?: throw invalidRefreshToken()

        token.revoke(now)
        refreshTokens.save(token)
        return issueTokens(user, token.familyId)
    }

    fun logout(refreshToken: String) {
        val token = refreshTokens.findByHash(TokenSecrets.hash(refreshToken)) ?: return
        refreshTokens.revokeFamily(token.familyId, clock.instant())
    }

    fun logoutEverywhere(userId: UserId) {
        refreshTokens.revokeAllOfUser(userId, clock.instant())
    }

    @Transactional(readOnly = true)
    fun currentUser(userId: UserId): User =
        users.findById(userId)?.takeIf { it.enabled } ?: throw UnauthorizedException("The account is not available")

    private fun issueTokens(user: User, familyId: TokenFamilyId): AuthTokens {
        val now = clock.instant()
        val access = accessTokens.issue(user, now)
        val secret = TokenSecrets.generate()
        val refresh = RefreshToken.issue(user.id, familyId, TokenSecrets.hash(secret), now + properties.refreshTokenTtl)
        refreshTokens.save(refresh)
        return AuthTokens(access.value, access.expiresAt, secret, refresh.expiresAt)
    }

    private fun invalidCredentials() = UnauthorizedException("Invalid email or password")

    private fun invalidRefreshToken() = UnauthorizedException("The session has expired. Please log in again.")

}
