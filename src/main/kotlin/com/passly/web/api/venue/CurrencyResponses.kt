package com.passly.web.api.venue

import com.passly.domain.venue.AcceptedCurrency
import com.passly.domain.venue.VenueSettings
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class CurrenciesResponse(
    @Schema(description = "ISO 4217 code of the main currency: menu prices and bills are in it", example = "CHF")
    val main: String,
    @Schema(description = "Other currencies guests may pay in")
    val accepted: List<AcceptedCurrencyResponse>,
)

data class AcceptedCurrencyResponse(
    @Schema(example = "EUR")
    val currency: String,
    @Schema(description = "Main currency units for one unit of this currency", example = "0.95")
    val rate: String,
    val rateSetAt: Instant,
)

fun VenueSettings.toCurrenciesResponse() = CurrenciesResponse(
    main = currency.value,
    accepted = acceptedCurrencies.map { it.toResponse() },
)

private fun AcceptedCurrency.toResponse() = AcceptedCurrencyResponse(currency.value, rate.toString(), rateSetAt)
