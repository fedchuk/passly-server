package com.passly.web.platform

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

class RegisterBusinessForm {
    @NotBlank(message = "Business name must not be blank")
    @Size(max = 160, message = "Business name is too long (max 160)")
    var businessName: String = ""

    @NotBlank(message = "Venue name must not be blank")
    @Size(max = 160, message = "Venue name is too long (max 160)")
    var venueName: String = ""

    @NotBlank(message = "Owner name must not be blank")
    @Size(max = 120, message = "Owner name is too long (max 120)")
    var ownerName: String = ""

    @NotBlank(message = "Email must not be blank")
    @Size(max = 254, message = "Email is too long")
    var ownerEmail: String = ""

    @NotBlank(message = "Password must not be blank")
    var ownerPassword: String = ""

    override fun toString() =
        "RegisterBusinessForm(businessName=$businessName, venueName=$venueName, ownerEmail=$ownerEmail, ownerPassword=***)"
}
