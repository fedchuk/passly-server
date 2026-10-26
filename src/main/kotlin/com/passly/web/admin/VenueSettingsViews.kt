package com.passly.web.admin

import com.passly.domain.venue.AcceptedCurrency
import com.passly.domain.venue.CountryCode
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.VenueSettings
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

data class CodeOption(
    val code: String,
    val label: String,
)

data class AcceptedCurrencyView(
    val code: String,
    val label: String,
    val rate: String,
    val rateSetOn: String,
)

private val RATE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC)

fun countryOptions(): List<CodeOption> =
    CountryCode.supported()
        .map { CodeOption(it.value, Locale.of("", it.value).getDisplayCountry(Locale.ENGLISH)) }
        .sortedBy { it.label }

fun currencyOptions(): List<CodeOption> =
    CurrencyCode.supported().map { CodeOption(it.value, currencyLabel(it)) }

fun VenueSettings.addableCurrencyOptions(): List<CodeOption> {
    val taken = acceptedCurrencies.map { it.currency } + currency
    return CurrencyCode.supported().filterNot { it in taken }.map { CodeOption(it.value, currencyLabel(it)) }
}

fun AcceptedCurrency.toView() = AcceptedCurrencyView(
    code = currency.value,
    label = currencyLabel(currency),
    rate = rate.toString(),
    rateSetOn = RATE_DATE.format(rateSetAt),
)

private fun currencyLabel(code: CurrencyCode): String =
    "${code.value} · ${Currency.getInstance(code.value).getDisplayName(Locale.ENGLISH)}"
