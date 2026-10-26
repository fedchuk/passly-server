package com.passly.domain.venue

import com.passly.domain.common.ValidationException
import java.util.*

@JvmInline
value class CurrencyCode private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        private const val SUPPORTED_DECIMALS = 2

        private val IN_USE: Set<String> = Locale.getISOCountries()
            .mapNotNull { country -> runCatching { Currency.getInstance(Locale.of("", country)) }.getOrNull() }
            .map { it.currencyCode }
            .toSortedSet()

        fun of(code: String): CurrencyCode {
            val normalized = code.trim().uppercase()
            if (normalized !in IN_USE) throw ValidationException("Unknown currency $code", "currency")
            val decimals = Currency.getInstance(normalized).defaultFractionDigits
            if (decimals != SUPPORTED_DECIMALS) {
                throw ValidationException(
                    "Currency $normalized has $decimals decimal places, only $SUPPORTED_DECIMALS are supported",
                    "currency",
                )
            }
            return CurrencyCode(normalized)
        }

        fun defaultFor(country: CountryCode): CurrencyCode? =
            runCatching { Currency.getInstance(Locale.of("", country.value)) }.getOrNull()
                ?.takeIf { it.defaultFractionDigits == SUPPORTED_DECIMALS }
                ?.let { CurrencyCode(it.currencyCode) }

        fun supported(): List<CurrencyCode> =
            IN_USE.filter { Currency.getInstance(it).defaultFractionDigits == SUPPORTED_DECIMALS }.map(::CurrencyCode)
    }
}
