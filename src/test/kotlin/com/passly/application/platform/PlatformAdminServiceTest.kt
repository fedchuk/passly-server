package com.passly.application.platform

import com.passly.application.user.FakePasswordHasher
import com.passly.application.user.FakeUserRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlatformAdminServiceTest {

    private val users = FakeUserRepository()
    private val service = PlatformAdminService(users, FakePasswordHasher())

    private fun register(email: String = "admin@passly.dev") =
        service.register(RegisterPlatformAdminCommand(Email.of(email), "Admin", RawPassword.of("password-1")))

    @Test
    fun `platform admin is stored with a hashed password and no venues`() {
        val admin = register()

        val stored = users.stored.getValue(admin.id)
        assertTrue(stored.platformAdmin)
        assertTrue(stored.memberships.isEmpty())
        assertEquals("hashed:password-1", stored.passwordHash)
    }

    @Test
    fun `email must be free`() {
        register()

        assertFailsWith<ConflictException> { register() }
        assertEquals(1, users.stored.size)
    }
}
