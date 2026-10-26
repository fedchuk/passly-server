package com.passly.domain.venue

import com.passly.domain.common.ConflictException

class VenueSettings private constructor(
    val venueId: VenueId,
    country: CountryCode,
    currency: CurrencyCode,
    val version: Long,
) {
    var country: CountryCode = country
        private set

    var currency: CurrencyCode = currency
        private set

    fun changeCountry(country: CountryCode) {
        this.country = country
    }

    fun changeCurrency(currency: CurrencyCode, venueHasOrders: Boolean) {
        if (currency == this.currency) return
        if (venueHasOrders) {
            throw ConflictException("The main currency cannot change after the first order: menu prices and bills are in ${this.currency}")
        }
        this.currency = currency
    }

    override fun toString() = "VenueSettings($venueId, $country, $currency)"

    companion object {
        fun fromStorage(venueId: VenueId, country: CountryCode, currency: CurrencyCode, version: Long) =
            VenueSettings(venueId, country, currency, version)
    }
}
