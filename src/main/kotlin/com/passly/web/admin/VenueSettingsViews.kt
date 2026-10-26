package com.passly.web.admin

import com.passly.domain.venue.CountryCode
import com.passly.domain.venue.CurrencyCode
import java.util.*

data class CodeOption(
    val code: String,
    val label: String,
)

fun countryOptions(): List<CodeOption> =
    CountryCode.supported()
        .map { CodeOption(it.value, Locale.of("", it.value).getDisplayCountry(Locale.ENGLISH)) }
        .sortedBy { it.label }

fun currencyOptions(): List<CodeOption> =
    CurrencyCode.supported()
        .map { CodeOption(it.value, "${it.value} · ${Currency.getInstance(it.value).getDisplayName(Locale.ENGLISH)}") }
