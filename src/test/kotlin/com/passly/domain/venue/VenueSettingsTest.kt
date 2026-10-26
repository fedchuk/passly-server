package com.passly.domain.venue

import com.passly.domain.common.ConflictException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VenueSettingsTest {

    private fun settings() =
        VenueSettings.fromStorage(VenueId.new(), CountryCode.of("FR"), CurrencyCode.of("EUR"), version = 0)

    @Test
    fun `currency changes while the venue has no orders`() {
        val settings = settings()

        settings.changeCountry(CountryCode.of("CH"))
        settings.changeCurrency(CurrencyCode.of("CHF"), venueHasOrders = false)

        assertEquals("CH", settings.country.value)
        assertEquals("CHF", settings.currency.value)
    }

    @Test
    fun `currency is fixed after the first order`() {
        val settings = settings()

        assertFailsWith<ConflictException> { settings.changeCurrency(CurrencyCode.of("CHF"), venueHasOrders = true) }
        assertEquals("EUR", settings.currency.value)
    }

    @Test
    fun `same currency is accepted after the first order`() {
        val settings = settings()

        settings.changeCurrency(CurrencyCode.of("EUR"), venueHasOrders = true)

        assertEquals("EUR", settings.currency.value)
    }

    @Test
    fun `country changes after the first order`() {
        val settings = settings()

        settings.changeCountry(CountryCode.of("BE"))

        assertEquals("BE", settings.country.value)
    }
}
