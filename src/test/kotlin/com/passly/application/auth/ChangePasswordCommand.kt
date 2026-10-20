package com.passly.application.auth

import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserId

data class ChangePasswordCommand(
    val userId: UserId,
    val currentPassword: String,
    val newPassword: RawPassword,
) {
    override fun toString() = "ChangePasswordCommand(userId=$userId, currentPassword=***, newPassword=***)"
}
