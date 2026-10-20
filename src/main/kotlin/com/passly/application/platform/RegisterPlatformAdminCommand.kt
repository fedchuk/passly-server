package com.passly.application.platform

import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword

data class RegisterPlatformAdminCommand(
    val email: Email,
    val displayName: String,
    val password: RawPassword,
)
