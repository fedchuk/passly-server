package com.passly.config

import com.passly.application.platform.PlatformAdminService
import com.passly.application.platform.RegisterPlatformAdminCommand
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class PlatformAdminBootstrapRunner(
    private val properties: PlatformAdminProperties,
    private val platformAdmins: PlatformAdminService,
    private val users: UserRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(PlatformAdminBootstrapRunner::class.java)

    override fun run(args: ApplicationArguments) {
        if (!properties.enabled) return
        val email = Email.of(properties.email)
        if (users.findByEmail(email) != null) {
            log.info("Platform admin bootstrap skipped: {} already exists", email)
            return
        }
        platformAdmins.register(
            RegisterPlatformAdminCommand(
                email,
                properties.name,
                RawPassword.of(properties.password)
            )
        )
        log.info("Platform admin {} created", email)
    }
}
