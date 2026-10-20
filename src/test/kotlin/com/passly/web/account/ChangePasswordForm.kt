package com.passly.web.account

import jakarta.validation.constraints.NotBlank

class ChangePasswordForm {
    @NotBlank(message = "Enter your current password")
    var currentPassword: String = ""

    @NotBlank(message = "Enter a new password")
    var newPassword: String = ""

    @NotBlank(message = "Repeat the new password")
    var confirmPassword: String = ""

    override fun toString() = "ChangePasswordForm(***)"
}
