package com.passly.domain.venue

import com.passly.domain.common.ConflictException
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.ValidationException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VenueSettingsTest {

    private val monday = Instant.parse("2026-10-26T09:00:00Z")
    private val tuesday = Instant.parse("2026-10-27T09:00:00Z")

    private fun settings(main: String = "EUR") =
        VenueSettings.fromStorage(VenueId.new(), CountryCode.of("FR"), CurrencyCode.of(main), version = 0)

    private fun rate(value: String) = ExchangeRate.of(value)

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

    @Test
    fun `additional currency is accepted with its rate`() {
        val settings = settings("CHF")

        settings.acceptCurrency(CurrencyCode.of("EUR"), rate("0.95"), monday)

        assertEquals(listOf(AcceptedCurrency(CurrencyCode.of("EUR"), rate("0.95"), monday)), settings.acceptedCurrencies)
    }

    @Test
    fun `main currency is not an additional one`() {
        val settings = settings("CHF")

        val error = assertFailsWith<ValidationException> { settings.acceptCurrency(CurrencyCode.of("CHF"), rate("1"), monday) }
        assertEquals("currency", error.field)
    }

    @Test
    fun `currency is accepted once`() {
        val settings = settings("CHF")
        settings.acceptCurrency(CurrencyCode.of("EUR"), rate("0.95"), monday)

        assertFailsWith<ConflictException> { settings.acceptCurrency(CurrencyCode.of("EUR"), rate("0.96"), tuesday) }
    }

    @Test
    fun `venue accepts at most five additional currencies`() {
        val settings = settings("CHF")
        listOf("EUR", "USD", "GBP", "SEK", "NOK").forEach { settings.acceptCurrency(CurrencyCode.of(it), rate("1"), monday) }

        assertFailsWith<ConflictException> { settings.acceptCurrency(CurrencyCode.of("DKK"), rate("0.13"), monday) }
    }

    @Test
    fun `new rate remembers when it was set`() {
        val settings = settings("CHF")
        settings.acceptCurrency(CurrencyCode.of("EUR"), rate("0.95"), monday)

        settings.changeRate(CurrencyCode.of("EUR"), rate("0.94"), tuesday)

        assertEquals(AcceptedCurrency(CurrencyCode.of("EUR"), rate("0.94"), tuesday), settings.accepted(CurrencyCode.of("EUR")))
    }

    @Test
    fun `same rate keeps the old date`() {
        val settings = settings("CHF")
        settings.acceptCurrency(CurrencyCode.of("EUR"), rate("0.95"), monday)

        settings.changeRate(CurrencyCode.of("EUR"), rate("0.950"), tuesday)

        assertEquals(monday, settings.accepted(CurrencyCode.of("EUR")).rateSetAt)
    }

    @Test
    fun `currency stops being accepted`() {
        val settings = settings("CHF")
        settings.acceptCurrency(CurrencyCode.of("EUR"), rate("0.95"), monday)

        settings.stopAccepting(CurrencyCode.of("EUR"))

        assertTrue(settings.acceptedCurrencies.isEmpty())
        assertFailsWith<NotFoundException> { settings.stopAccepting(CurrencyCode.of("EUR")) }
    }

    @Test
    fun `main currency stays while additional currencies are set against it`() {
        val settings = settings("CHF")
        settings.acceptCurrency(CurrencyCode.of("EUR"), rate("0.95"), monday)

        assertFailsWith<ConflictException> { settings.changeCurrency(CurrencyCode.of("EUR"), venueHasOrders = false) }
        assertEquals("CHF", settings.currency.value)
    }
}
