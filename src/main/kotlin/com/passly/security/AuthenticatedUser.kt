package com.passly.security

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

fun Authentication.userIdOrNull(): UUID? = when (val principal = principal) {
    is PasslyUserPrincipal -> principal.userId
    is Jwt -> principal.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    else -> null
}
