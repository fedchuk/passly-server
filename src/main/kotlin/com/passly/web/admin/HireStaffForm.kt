package com.passly.web.admin

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.*

class HireStaffForm {
    @NotBlank(message = "Email must not be blank")
    @Size(max = 254, message = "Email is too long")
    var email: String = ""

    @NotBlank(message = "Name must not be blank")
    @Size(max = 120, message = "Name is too long (max 120)")
    var displayName: String = ""

    @NotBlank(message = "Password must not be blank")
    var password: String = ""

    @NotNull(message = "Choose a role")
    var roleId: UUID? = null

    override fun toString() = "HireStaffForm(email=$email, displayName=$displayName, password=***, roleId=$roleId)"
}
