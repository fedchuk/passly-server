package com.passly.domain.user

import com.passly.domain.common.ValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmailTest {

    @Test
    fun `email is trimmed and lowercased`() {
        assertEquals("anna@bistro.es", Email.of("  Anna@Bistro.ES ").value)
    }

    @Test
    fun `malformed emails are rejected`() {
        listOf("", "anna", "anna@", "anna@bistro", "an na@bistro.es").forEach {
            assertFailsWith<ValidationException>(it) { Email.of(it) }
        }
    }
}
