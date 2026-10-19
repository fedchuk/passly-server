package com.passly.security

import com.passly.domain.user.PasswordHasher
import com.passly.domain.user.RawPassword
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.stereotype.Component

@Component
class SpringPasswordHasher : PasswordHasher {

    private val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    override fun hash(password: RawPassword): String =
        checkNotNull(encoder.encode(password.value)) { "Password encoder returned no hash" }

    override fun matches(password: String, hash: String): Boolean =
        encoder.matches(password, hash)
}
