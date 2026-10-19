package com.passly.web.api.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @NotBlank @Size(max = 254) val email: String,
    @NotBlank @Size(max = 200) val password: String,
) {
    override fun toString() = "LoginRequest(email=$email, password=***)"
}

data class RefreshTokenRequest(
    @NotBlank @Size(max = 200) val refreshToken: String,
) {
    override fun toString() = "RefreshTokenRequest(***)"
}
