package com.passly.domain.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object TokenSecrets {
    private const val SECRET_BYTES = 32
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(SECRET_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(secret: String): String =
        MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()).toHexString()
}
