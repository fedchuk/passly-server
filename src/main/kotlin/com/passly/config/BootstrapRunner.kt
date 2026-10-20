package com.passly.config

import com.passly.application.venue.OnboardingService
import com.passly.application.venue.RegisterBusinessCommand
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class BootstrapRunner(
    private val properties: BootstrapProperties,
    private val onboarding: OnboardingService,
    private val users: UserRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(BootstrapRunner::class.java)

    override fun run(args: ApplicationArguments) {
        if (!properties.enabled) return
        val email = Email.of(properties.ownerEmail)
        if (users.findByEmail(email) != null) {
            log.info("Bootstrap skipped: {} already exists", email)
            return
        }
        val business = onboarding.registerBusiness(
            RegisterBusinessCommand(
                businessName = properties.businessName,
                venueName = properties.venueName,
                ownerEmail = email,
                ownerName = properties.ownerName,
                ownerPassword = RawPassword.of(properties.ownerPassword),
            ),
        )
        log.info("Bootstrap created business {} with owner {}", business.tenantId, email)
    }
}
