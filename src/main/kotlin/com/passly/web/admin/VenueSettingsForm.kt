package com.passly.web.admin

import com.passly.domain.venue.VenueSettings
import jakarta.validation.constraints.NotBlank

class VenueSettingsForm {
    @NotBlank(message = "Choose a country")
    var country: String = ""

    @NotBlank(message = "Choose a currency")
    var currency: String = ""

    var timeZone: String = ""

    var version: Long? = null

    companion object {
        fun of(settings: VenueSettings) = VenueSettingsForm().apply {
            country = settings.country.value
            currency = settings.currency.value
            timeZone = settings.timeZone.id
            version = settings.version
        }
    }
}