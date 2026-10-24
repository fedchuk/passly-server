package com.passly.application.user

import com.passly.domain.common.UnauthorizedException
import com.passly.domain.user.Email
import com.passly.domain.user.User
import com.passly.domain.user.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProfileServiceTest {

    private val users = FakeUserRepository()
    private val service = ProfileService(users)

    private fun stored(user: User): User = user.also { users.save(it) }

    @Test
    fun `rename stores the new name`() {
        val max = stored(User.create(Email.of("max@test.passly"), "Max", "hash"))

        service.rename(RenameProfileCommand(max.id, " Maksym "))

        assertEquals("Maksym", users.stored.getValue(max.id).displayName)
    }

    @Test
    fun `disabled or unknown user cannot change the profile`() {
        val gone = User.create(Email.of("gone@test.passly"), "Gone", "hash").apply { disable() }
        stored(gone)

        assertFailsWith<UnauthorizedException> { service.rename(RenameProfileCommand(gone.id, "Back")) }
        assertFailsWith<UnauthorizedException> { service.get(UserId.new()) }
    }
}
