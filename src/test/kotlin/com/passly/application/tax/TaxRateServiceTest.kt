package com.passly.application.tax

import com.passly.application.menu.FakeVenueRepository
import com.passly.application.venue.FakeVenueSettingsRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.common.TaxRate
import com.passly.domain.venue.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TaxRateServiceTest {

    private val venue = Venue(VenueId.new(), TenantId.new(), "Bistro")
    private val venues = FakeVenueRepository(venue)
    private val settings = FakeVenueSettingsRepository(
        VenueSettings.fromStorage(venue.id, CountryCode.of("CH"), CurrencyCode.of("CHF"), version = 0),
    )
    private val taxRates = FakeTaxRateRepository()
    private val service = TaxRateService(venues, settings, taxRates)

    @Test
    fun `rate is added under a lock of the venue`() {
        val wine = service.create(CreateTaxRateCommand(venue.id, "Wine", TaxRate.of("8.1")))

        assertEquals(listOf("Wine"), service.list(venue.id).map { it.name })
        assertEquals(venue.tenantId, wine.tenantId)
        assertEquals(1, venues.locks)
    }

    @Test
    fun `presets follow the country of the venue`() {
        assertEquals(listOf("Standard", "Reduced"), service.presets(venue.id).map { it.name })

        service.addPresets(venue.id)

        assertEquals(listOf("Standard", "Reduced"), service.list(venue.id).map { it.name })
        assertTrue(service.presets(venue.id).isEmpty())
    }

    @Test
    fun `rate is changed and a stale form is refused`() {
        val standard = service.create(CreateTaxRateCommand(venue.id, "Standard", TaxRate.of("7.7")))
        val stored = service.list(venue.id).single()

        service.change(ChangeTaxRateCommand(venue.id, standard.id, "Standard", TaxRate.of("8.1"), stored.version))

        assertEquals("8.1%", service.list(venue.id).single().percentLabel)
        assertFailsWith<ConflictException> {
            service.change(ChangeTaxRateCommand(venue.id, standard.id, "Standard", TaxRate.of("9"), stored.version))
        }
    }

    @Test
    fun `rate is deleted`() {
        val standard = service.create(CreateTaxRateCommand(venue.id, "Standard", TaxRate.of("8.1")))

        service.delete(venue.id, standard.id)

        assertTrue(service.list(venue.id).isEmpty())
    }
}
