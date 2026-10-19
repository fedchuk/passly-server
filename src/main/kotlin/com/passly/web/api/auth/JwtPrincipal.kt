package com.passly.web.api.auth

import com.passly.domain.user.UserId
import org.springframework.security.oauth2.jwt.Jwt
import java.util.UUID

fun Jwt.userId() = UserId(UUID.fromString(subject))
