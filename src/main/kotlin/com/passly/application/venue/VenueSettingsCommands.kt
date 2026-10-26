package com.passly.application.venue

import com.passly.domain.venue.CountryCode
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.VenueId

data class UpdateVenueSettingsCommand(
    val venueId: VenueId,
    val country: CountryCode,
    val currency: CurrencyCode,
    val expectedVersion: Long? = null,
)
