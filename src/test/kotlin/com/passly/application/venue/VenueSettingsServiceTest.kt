package com.passly.application.venue

import com.passly.application.floor.FakeFloorPlanRepository
import com.passly.application.menu.FakeMenuChangeRecorder
import com.passly.application.menu.FakeVenueRepository
import com.passly.application.menu.MenuChangeKind
import com.passly.application.order.FakeOrderRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.order.Order
import com.passly.domain.user.UserId
import com.passly.domain.venue.*
import java.time.Instant
import kotlin.test.*

class VenueSettingsServiceTest {

    private val venue = Venue(VenueId.new(), TenantId.new(), "Bistro")
    private val venues = FakeVenueRepository(venue)
    private val settings = FakeVenueSettingsRepository(
        VenueSettings.fromStorage(venue.id, CountryCode.of("FR"), CurrencyCode.of("EUR"), version = 3),
    )
    private val orders = FakeOrderRepository()
    private val menuChanges = FakeMenuChangeRecorder()
    private val service = VenueSettingsService(venues, settings, orders, menuChanges)

    private fun update(country: String, currency: String, version: Long? = null) = service.update(
        UpdateVenueSettingsCommand(venue.id, CountryCode.of(country), CurrencyCode.of(currency), version),
    )

    private fun openAnOrder() {
        val floors = FakeFloorPlanRepository()
        val plan = floors.load(venue.id)
        val hall = plan.addArea(venue.tenantId, "Hall")
        val table = plan.addTable(venue.tenantId, hall.id, "7", 4)
        orders.save(Order.open(table, guests = 2, openedBy = UserId.new(), openedAt = Instant.parse("2026-10-26T19:00:00Z")))
    }

    @Test
    fun `country and currency are saved and the menu learns the new currency`() {
        val saved = update("CH", "CHF", version = 3)

        assertEquals("CH", saved.country.value)
        assertEquals("CHF", saved.currency.value)
        assertEquals(4, saved.version)
        assertEquals(listOf(venue.id to MenuChangeKind.CURRENCY), menuChanges.recorded)
        assertEquals(1, venues.locks)
    }

    @Test
    fun `country alone does not touch the menu`() {
        update("BE", "EUR")

        assertEquals("BE", service.get(venue.id).country.value)
        assertTrue(menuChanges.recorded.isEmpty())
    }

    @Test
    fun `currency is fixed once the venue has an order`() {
        openAnOrder()

        assertTrue(service.currencyLocked(venue.id))
        assertFailsWith<ConflictException> { update("CH", "CHF") }
        assertEquals("EUR", service.get(venue.id).currency.value)
    }

    @Test
    fun `stale form is refused`() {
        assertFailsWith<ConflictException> { update("CH", "CHF", version = 2) }
        assertFalse(service.currencyLocked(venue.id))
    }
}
