package com.passly.application.venue

import com.passly.domain.venue.CountryCode
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.ExchangeRate
import com.passly.domain.venue.VenueId

data class UpdateVenueSettingsCommand(
    val venueId: VenueId,
    val country: CountryCode,
    val currency: CurrencyCode,
    val expectedVersion: Long? = null,
)

data class AcceptCurrencyCommand(
    val venueId: VenueId,
    val currency: CurrencyCode,
    val rate: ExchangeRate,
)

data class ChangeRateCommand(
    val venueId: VenueId,
    val currency: CurrencyCode,
    val rate: ExchangeRate,
)
