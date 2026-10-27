package com.passly.application.venue

import com.passly.TestcontainersConfiguration
import com.passly.application.access.AccessService
import com.passly.application.auth.AuthService
import com.passly.application.auth.LoginCommand
import com.passly.domain.common.ConflictException
import com.passly.domain.role.VenueAccess
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OnboardingServiceIntegrationTest {

    @Autowired
    lateinit var onboarding: OnboardingService

    @Autowired
    lateinit var auth: AuthService

    @Autowired
    lateinit var access: AccessService

    private val email = Email.of("owner-${UUID.randomUUID().toString().take(8)}@bistro.es")

    private fun register() = onboarding.registerBusiness(
        RegisterBusinessCommand("Bistro Group", "Bistro Centro", email, "Max", RawPassword.of("password-1")),
    )

    @Test
    fun `registered owner can log in and sees the new venue`() {
        val business = register()

        auth.login(LoginCommand(email, "password-1"))
        val venues = access.venuesOf(business.ownerId)

        assertEquals(listOf("Bistro Centro"), venues.map { it.name })
        assertEquals(VenueAccess.OWNER, venues.single().access)
    }

    @Test
    fun `the same owner email cannot register twice`() {
        register()

        assertFailsWith<ConflictException> { register() }
    }
}
