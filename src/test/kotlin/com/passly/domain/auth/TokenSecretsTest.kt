package com.passly.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TokenSecretsTest {

    @Test
    fun `secrets are random and url safe`() {
        val secrets = List(100) { TokenSecrets.generate() }

        assertEquals(100, secrets.toSet().size)
        assertEquals(true, secrets.all { it.matches(Regex("[A-Za-z0-9_-]{43}")) })
    }

    @Test
    fun `hash is stable and does not reveal the secret`() {
        val secret = TokenSecrets.generate()

        assertEquals(TokenSecrets.hash(secret), TokenSecrets.hash(secret))
        assertNotEquals(secret, TokenSecrets.hash(secret))
        assertEquals(64, TokenSecrets.hash(secret).length)
    }
}
