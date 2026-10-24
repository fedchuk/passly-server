package com.passly.web.admin

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

class StationForm {
    @NotBlank(message = "Station name must not be blank")
    @Size(max = 80, message = "Station name is too long (max 80)")
    var name: String = ""

    var version: Long? = null
}
