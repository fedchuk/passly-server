package com.passly.config

import com.passly.application.platform.PlatformAdminService
import com.passly.application.user.FakePasswordHasher
import com.passly.application.user.FakeUserRepository
import org.springframework.boot.DefaultApplicationArguments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformAdminBootstrapRunnerTest {

    private val users = FakeUserRepository()
    private val service = PlatformAdminService(users, FakePasswordHasher())

    private fun runner(email: String) =
        PlatformAdminBootstrapRunner(PlatformAdminProperties(email, "Admin", "password-1"), service, users)

    @Test
    fun `creates the platform admin once`() {
        val runner = runner("admin@passly.dev")

        runner.run(DefaultApplicationArguments())
        runner.run(DefaultApplicationArguments())

        assertEquals(1, users.stored.size)
        assertTrue(users.stored.values.single().platformAdmin)
    }

    @Test
    fun `does nothing without an email`() {
        runner("").run(DefaultApplicationArguments())

        assertTrue(users.stored.isEmpty())
    }
}
