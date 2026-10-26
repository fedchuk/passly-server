package com.passly.domain.venue

import com.passly.domain.common.ValidationException
import java.util.*

@JvmInline
value class CountryCode private constructor(val value: String) {

    val defaultCurrency: CurrencyCode?
        get() = CurrencyCode.defaultFor(this)

    override fun toString(): String = value

    companion object {
        private val ISO_CODES: Set<String> = Locale.getISOCountries().toSet()

        val FRANCE = CountryCode("FR")

        fun of(code: String): CountryCode {
            val normalized = code.trim().uppercase()
            if (normalized !in ISO_CODES) throw ValidationException("Unknown country $code", "country")
            return CountryCode(normalized)
        }

        fun supported(): List<CountryCode> =
            ISO_CODES.map(::CountryCode).filter { it.defaultCurrency != null }
    }
}
