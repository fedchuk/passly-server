package com.passly.web.admin

import com.passly.domain.user.Role
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

class HireStaffForm {
    @NotBlank(message = "Email must not be blank")
    @Size(max = 254, message = "Email is too long")
    var email: String = ""

    @NotBlank(message = "Name must not be blank")
    @Size(max = 120, message = "Name is too long (max 120)")
    var displayName: String = ""

    @NotBlank(message = "Password must not be blank")
    var password: String = ""

    var role: Role = Role.WAITER

    override fun toString() = "HireStaffForm(email=$email, displayName=$displayName, password=***, role=$role)"
}
