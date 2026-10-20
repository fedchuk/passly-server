package com.passly.config

import com.passly.application.menu.FakeVenueRepository
import com.passly.application.user.FakePasswordHasher
import com.passly.application.user.FakeUserRepository
import com.passly.application.venue.FakeTenantRepository
import com.passly.application.venue.OnboardingService
import org.springframework.boot.DefaultApplicationArguments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BootstrapRunnerTest {

    private val tenants = FakeTenantRepository()
    private val users = FakeUserRepository()
    private val onboarding = OnboardingService(tenants, FakeVenueRepository(), users, FakePasswordHasher())

    private fun runner(email: String) = BootstrapRunner(
        BootstrapProperties("Bistro Group", "Bistro Centro", email, "Max", "password-1"),
        onboarding,
        users,
    )

    @Test
    fun `creates the first business once`() {
        val runner = runner("max@bistro.es")

        runner.run(DefaultApplicationArguments())
        runner.run(DefaultApplicationArguments())

        assertEquals(1, tenants.stored.size)
        assertEquals(1, users.stored.size)
    }

    @Test
    fun `does nothing without an owner email`() {
        runner("").run(DefaultApplicationArguments())

        assertTrue(tenants.stored.isEmpty())
    }
}
