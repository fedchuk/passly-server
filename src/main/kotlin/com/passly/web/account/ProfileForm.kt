package com.passly.web.account

import com.passly.domain.user.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

class ProfileForm {
    @NotBlank(message = "Name must not be blank")
    @Size(max = 120, message = "Name is too long (max 120)")
    var displayName: String = ""

    companion object {
        fun of(user: User) = ProfileForm().apply { displayName = user.displayName }
    }
}
