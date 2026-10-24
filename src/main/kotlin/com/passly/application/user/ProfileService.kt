package com.passly.application.user

import com.passly.domain.common.UnauthorizedException
import com.passly.domain.user.User
import com.passly.domain.user.UserId
import com.passly.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ProfileService(private val users: UserRepository) {

    @Transactional(readOnly = true)
    fun get(userId: UserId): User = requireUser(userId)

    fun rename(command: RenameProfileCommand): User {
        val user = requireUser(command.userId)
        user.rename(command.displayName)
        users.save(user)
        return user
    }

    private fun requireUser(id: UserId): User =
        users.findById(id)?.takeIf { it.enabled } ?: throw UnauthorizedException("The account is not available")
}
