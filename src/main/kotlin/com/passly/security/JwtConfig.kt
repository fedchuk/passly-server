package com.passly.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.passly.application.auth.AuthProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.*
import java.security.KeyPair
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@Configuration
class JwtConfig(
    private val authProperties: AuthProperties,
    jwtProperties: JwtProperties,
) {

    private val log = LoggerFactory.getLogger(JwtConfig::class.java)

    private val keyPair: KeyPair = loadKeyPair(jwtProperties)

    @Bean
    fun jwtEncoder(): JwtEncoder {
        val key = RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID(KEY_ID)
            .build()
        return NimbusJwtEncoder(ImmutableJWKSet(JWKSet(key)))
    }

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer(authProperties.issuer))
        }

    private fun loadKeyPair(properties: JwtProperties): KeyPair {
        if (properties.privateKey.isNotBlank()) return RsaKeys.keyPairFromPem(properties.privateKey)
        check(properties.generateIfMissing) { "JWT private key is not configured: set the JWT_PRIVATE_KEY environment variable" }
        log.warn("No JWT private key configured, using a temporary one. Issued tokens will not survive a restart.")
        return RsaKeys.generate()
    }

    private companion object {
        const val KEY_ID = "passly"
    }
}
