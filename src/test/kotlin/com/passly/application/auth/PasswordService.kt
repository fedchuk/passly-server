package com.passly.application.auth

import com.passly.domain.auth.RefreshTokenRepository
import com.passly.domain.common.UnauthorizedException
import com.passly.domain.common.ValidationException
import com.passly.domain.user.PasswordHasher
import com.passly.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
@Transactional
class PasswordService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwordHasher: PasswordHasher,
    private val clock: Clock,
) {

    fun change(command: ChangePasswordCommand) {
        val user = users.findById(command.userId)?.takeIf { it.enabled }
            ?: throw UnauthorizedException("The account is not available")
        if (!passwordHasher.matches(command.currentPassword, user.passwordHash)) {
            throw ValidationException("Current password is wrong", field = "currentPassword")
        }
        if (passwordHasher.matches(command.newPassword.value, user.passwordHash)) {
            throw ValidationException("New password must differ from the current one", field = "newPassword")
        }
        user.changePasswordHash(passwordHasher.hash(command.newPassword))
        users.save(user)
        refreshTokens.revokeAllOfUser(user.id, clock.instant())
    }
}
