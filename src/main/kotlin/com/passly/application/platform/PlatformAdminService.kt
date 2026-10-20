package com.passly.application.platform

import com.passly.domain.common.ConflictException
import com.passly.domain.user.PasswordHasher
import com.passly.domain.user.User
import com.passly.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PlatformAdminService(
    private val users: UserRepository,
    private val passwordHasher: PasswordHasher,
) {

    fun register(command: RegisterPlatformAdminCommand): User {
        if (users.findByEmail(command.email) != null) {
            throw ConflictException("A user with email ${command.email} already exists")
        }
        val admin = User.createPlatformAdmin(command.email, command.displayName, passwordHasher.hash(command.password))
        users.save(admin)
        return admin
    }
}
