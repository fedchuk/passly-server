package com.passly.domain.venue

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import java.time.Instant

class VenueSettings private constructor(
    val venueId: VenueId,
    country: CountryCode,
    currency: CurrencyCode,
    acceptedCurrencies: List<AcceptedCurrency>,
    val version: Long,
) {
    var country: CountryCode = country
        private set

    var currency: CurrencyCode = currency
        private set

    var acceptedCurrencies: List<AcceptedCurrency> = acceptedCurrencies
        private set

    fun changeCountry(country: CountryCode) {
        this.country = country
    }

    fun changeCurrency(currency: CurrencyCode, venueHasOrders: Boolean) {
        if (currency == this.currency) return
        if (venueHasOrders) {
            throw ConflictException("The main currency cannot change after the first order: menu prices and bills are in ${this.currency}")
        }
        if (acceptedCurrencies.isNotEmpty()) {
            throw ConflictException("Remove the additional currencies first: their rates are set against ${this.currency}")
        }
        this.currency = currency
    }

    fun acceptCurrency(currency: CurrencyCode, rate: ExchangeRate, at: Instant): AcceptedCurrency {
        if (currency == this.currency) throw ValidationException("$currency is already the main currency", "currency")
        if (acceptedCurrencies.any { it.currency == currency }) throw ConflictException("$currency is already accepted")
        if (acceptedCurrencies.size >= MAX_ACCEPTED) throw ConflictException("A venue accepts at most $MAX_ACCEPTED additional currencies")
        val accepted = AcceptedCurrency(currency, rate, at)
        acceptedCurrencies = acceptedCurrencies + accepted
        return accepted
    }

    fun changeRate(currency: CurrencyCode, rate: ExchangeRate, at: Instant): AcceptedCurrency {
        val current = accepted(currency)
        if (current.rate == rate) return current
        val updated = current.copy(rate = rate, rateSetAt = at)
        acceptedCurrencies = acceptedCurrencies.map { if (it.currency == currency) updated else it }
        return updated
    }

    fun stopAccepting(currency: CurrencyCode) {
        accepted(currency)
        acceptedCurrencies = acceptedCurrencies.filterNot { it.currency == currency }
    }

    fun accepted(currency: CurrencyCode): AcceptedCurrency =
        acceptedCurrencies.firstOrNull { it.currency == currency } ?: throw NotFoundException(
            "Accepted currency",
            currency
        )

    override fun toString() = "VenueSettings($venueId, $country, $currency, accepts ${acceptedCurrencies.map { it.currency }})"

    companion object {
        const val MAX_ACCEPTED = 5

        fun fromStorage(
            venueId: VenueId,
            country: CountryCode,
            currency: CurrencyCode,
            version: Long,
            acceptedCurrencies: List<AcceptedCurrency> = emptyList(),
        ) = VenueSettings(venueId, country, currency, acceptedCurrencies, version)
    }
}
