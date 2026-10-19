package com.passly.domain.user

import com.passly.domain.common.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RawPasswordTest {

    @Test
    fun `short password is rejected`() {
        assertFailsWith<ValidationException> { RawPassword.of("1234567") }
    }

    @Test
    fun `limit is counted in bytes, not characters`() {
        RawPassword.of("a".repeat(72))
        assertFailsWith<ValidationException> { RawPassword.of("ж".repeat(37)) }
    }

    @Test
    fun `password never shows up in logs`() {
        val command = "login(${RawPassword.of("secret-pass")})"

        assertEquals("login(RawPassword(***))", command)
    }
}
