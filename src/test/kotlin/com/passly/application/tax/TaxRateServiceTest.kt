package com.passly.application.tax

import com.passly.application.menu.FakeCategoryRepository
import com.passly.application.menu.FakeMenuChangeRecorder
import com.passly.application.menu.FakeMenuItemRepository
import com.passly.application.menu.FakeVenueRepository
import com.passly.application.menu.MenuChangeKind
import com.passly.application.venue.FakeVenueSettingsRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
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
    private val categories = FakeCategoryRepository()
    private val items = FakeMenuItemRepository()
    private val menuChanges = FakeMenuChangeRecorder()
    private val service = TaxRateService(venues, settings, taxRates, categories, items, menuChanges)

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

    @Test
    fun `rate used by a category is kept`() {
        val standard = service.create(CreateTaxRateCommand(venue.id, "Standard", TaxRate.of("8.1")))
        categories.saveAll(listOf(categories.loadTree(venue.id).add(venue.tenantId, "Food", stationId = StationId.new(), taxRateId = standard.id)))

        assertFailsWith<ConflictException> { service.delete(venue.id, standard.id) }
        assertEquals(1, service.list(venue.id).size)
    }

    @Test
    fun `rate used by a dish is kept`() {
        val standard = service.create(CreateTaxRateCommand(venue.id, "Standard", TaxRate.of("8.1")))
        val reduced = service.create(CreateTaxRateCommand(venue.id, "Reduced", TaxRate.of("2.6")))
        val food = categories.loadTree(venue.id).add(venue.tenantId, "Food", stationId = StationId.new(), taxRateId = standard.id)
            .also { categories.saveAll(listOf(it)) }
        items.saveAll(
            listOf(items.loadCategoryItems(venue.id, food.id).add(venue.tenantId, MenuItemDetails("Bread", null, Money.of("3.00"), taxRateId = reduced.id))),
        )

        val error = assertFailsWith<ConflictException> { service.delete(venue.id, reduced.id) }

        assertEquals("Tax rate 'Reduced' is used by dishes. Choose another rate for them first.", error.message)
    }

    @Test
    fun `new percent changes the waiter menu and a new name does not`() {
        val standard = service.create(CreateTaxRateCommand(venue.id, "Standard", TaxRate.of("7.7")))

        service.change(ChangeTaxRateCommand(venue.id, standard.id, "Normal", TaxRate.of("7.70")))
        assertTrue(menuChanges.recorded.isEmpty())

        service.change(ChangeTaxRateCommand(venue.id, standard.id, "Normal", TaxRate.of("8.1")))
        assertEquals(listOf(venue.id to MenuChangeKind.TAX_RATES), menuChanges.recorded)
    }
}
