package com.passly.domain.user

interface PasswordHasher {
    fun hash(password: RawPassword): String
    fun matches(password: String, hash: String): Boolean
}
