package com.passly.web.api.auth

import com.passly.application.auth.AuthService
import com.passly.application.auth.LoginCommand
import com.passly.domain.user.Email
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Auth", description = "Log in, refresh and log out")
@RequestMapping("/api/v1/auth")
class AuthController(private val auth: AuthService) {

    @SecurityRequirements
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse =
        auth.login(LoginCommand(Email.of(request.email), request.password)).toResponse()

    @SecurityRequirements
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): TokenResponse =
        auth.refresh(request.refreshToken).toResponse()

    @SecurityRequirements
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody request: RefreshTokenRequest) {
        auth.logout(request.refreshToken)
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logoutEverywhere(@AuthenticationPrincipal jwt: Jwt) {
        auth.logoutEverywhere(jwt.userId())
    }
}
