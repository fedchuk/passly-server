package com.passly.web.api.auth

import com.passly.application.auth.AuthService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MeController(private val auth: AuthService) {

    @GetMapping("/api/v1/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): MeResponse =
        auth.currentUser(jwt.userId()).toMeResponse()
}
