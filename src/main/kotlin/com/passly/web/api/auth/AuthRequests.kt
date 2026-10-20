package com.passly.web.api.auth

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @Schema(example = "owner@passly.demo")
    @NotBlank @Size(max = 254) val email: String,
    @Schema(example = "password-1", format = "password")
    @NotBlank @Size(max = 200) val password: String,
) {
    override fun toString() = "LoginRequest(email=$email, password=***)"
}

data class RefreshTokenRequest(
    @Schema(
        description = "The refresh token from the login or the previous refresh. Each token works only once.",
        example = "xqC7IqvPpWt-10N_nq8bq2FwgpdqEr32ELvBAZW1so0",
    )
    @NotBlank @Size(max = 200) val refreshToken: String,
) {
    override fun toString() = "RefreshTokenRequest(***)"
}
