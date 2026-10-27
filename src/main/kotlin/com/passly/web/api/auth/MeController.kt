package com.passly.web.api.auth

import com.passly.application.access.AccessService
import com.passly.application.auth.AuthService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Profile", description = "The current user")
class MeController(
    private val auth: AuthService,
    private val access: AccessService,
) {

    @GetMapping("/api/v1/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): MeResponse {
        val user = auth.currentUser(jwt.userId())
        return user.toMeResponse(access.venuesOf(user.id))
    }
}
