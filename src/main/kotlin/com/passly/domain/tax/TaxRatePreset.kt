package com.passly.domain.tax

import com.passly.domain.common.TaxRate
import com.passly.domain.venue.CountryCode

data class TaxRatePreset(
    val name: String,
    val rate: TaxRate,
) {
    companion object {
        private val BY_COUNTRY: Map<String, List<TaxRatePreset>> = mapOf(
            "FR" to listOf(
                TaxRatePreset("Food and soft drinks", TaxRate.of("10")),
                TaxRatePreset("Alcohol", TaxRate.of("20")),
                TaxRatePreset("Reduced", TaxRate.of("5.5")),
            ),
            "CH" to listOf(
                TaxRatePreset("Standard", TaxRate.of("8.1")),
                TaxRatePreset("Reduced", TaxRate.of("2.6")),
            ),
        )

        fun forCountry(country: CountryCode): List<TaxRatePreset> = BY_COUNTRY[country.value].orEmpty()
    }
}
