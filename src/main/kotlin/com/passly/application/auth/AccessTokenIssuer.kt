package com.passly.application.auth

import com.passly.domain.user.User
import java.time.Instant

interface AccessTokenIssuer {
    fun issue(user: User, issuedAt: Instant): IssuedAccessToken
}

data class IssuedAccessToken(val value: String, val expiresAt: Instant)
