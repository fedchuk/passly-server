package com.passly.application.auth

import com.passly.domain.user.Email

data class LoginCommand(val email: Email, val password: String) {
    override fun toString() = "LoginCommand(email=$email, password=***)"
}
