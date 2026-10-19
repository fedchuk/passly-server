package com.passly.security

import com.passly.application.auth.AccessTokenIssuer
import com.passly.application.auth.AuthProperties
import com.passly.application.auth.IssuedAccessToken
import com.passly.domain.user.User
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class JwtAccessTokenIssuer(
    private val encoder: JwtEncoder,
    private val properties: AuthProperties,
) : AccessTokenIssuer {

    override fun issue(user: User, issuedAt: Instant): IssuedAccessToken {
        val expiresAt = issuedAt + properties.accessTokenTtl
        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .subject(user.id.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("email", user.email.value)
            .build()
        val header = JwsHeader.with(SignatureAlgorithm.RS256).build()
        val token = encoder.encode(JwtEncoderParameters.from(header, claims))
        return IssuedAccessToken(token.tokenValue, expiresAt)
    }
}
