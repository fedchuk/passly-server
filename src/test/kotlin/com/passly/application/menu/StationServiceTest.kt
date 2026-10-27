package com.passly.application.menu

import com.passly.application.order.FakeOrderRepository
import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.NotFoundException
import com.passly.domain.common.TaxRate
import com.passly.domain.floor.FloorPlan
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.priceLine
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.user.UserId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StationServiceTest {

    private val venue = Venue(VenueId.new(), TenantId.new(), "Bistro")

    private val venues = FakeVenueRepository(venue)
    private val stations = FakeStationRepository()
    private val categories = FakeCategoryRepository()
    private val items = FakeMenuItemRepository()
    private val orders = FakeOrderRepository()
    private val service = StationService(venues, stations, categories, items, orders)

    private fun station(name: String) = service.create(CreateStationCommand(venue.id, name))

    @Test
    fun `stations are created under the venue's tenant with the venue locked`() {
        val kitchen = station("Kitchen")

        assertEquals(venue.tenantId, stations.stored.getValue(kitchen.id).tenantId)
        assertEquals(1, venues.locks)
    }

    @Test
    fun `rename with an outdated version is rejected`() {
        val kitchen = station("Kitchen")

        assertFailsWith<ConflictException> {
            service.rename(RenameStationCommand(venue.id, kitchen.id, "Hot kitchen", expectedVersion = 5))
        }
        assertEquals("Kitchen", stations.stored.getValue(kitchen.id).name)
    }

    @Test
    fun `a station used by a category is not deleted`() {
        val kitchen = station("Kitchen")
        categories.saveAll(listOf(categories.loadTree(venue.id).add(venue.tenantId, "Food", stationId = kitchen.id, taxRateId = VenueTaxRateId.new())))

        assertFailsWith<ConflictException> { service.delete(venue.id, kitchen.id) }
        assertTrue(kitchen.id in stations.stored)
    }

    @Test
    fun `a station used by a menu item or an order line is not deleted`() {
        val bar = station("Bar")
        val pastry = station("Pastry")
        val drinks = categories.loadTree(venue.id).add(venue.tenantId, "Drinks", stationId = bar.id, taxRateId = VenueTaxRateId.new()).also { categories.saveAll(listOf(it)) }
        val wine = items.loadCategoryItems(venue.id, drinks.id)
            .add(venue.tenantId, MenuItemDetails("Rioja", null, Money.of("4.50"), bar.id))
            .also { items.saveAll(listOf(it)) }
        val floor = FloorPlan(venue.id, emptyList(), emptyList())
        val table = floor.addTable(venue.tenantId, floor.addArea(venue.tenantId, "Hall").id, "1", 2)
        val order = Order.open(table, null, UserId.new(), Instant.parse("2026-10-24T12:00:00Z"))
        order.addRound(listOf(wine.priceLine(OrderLineRequest(wine.id, 1), emptyMap(), pastry.id, TaxRate.of("21"))), UserId.new(), order.openedAt)
        orders.save(order)

        assertFailsWith<ConflictException> { service.delete(venue.id, bar.id) }
        assertFailsWith<ConflictException> { service.delete(venue.id, pastry.id) }
    }

    @Test
    fun `deleting a free station closes the gap`() {
        val kitchen = station("Kitchen")
        val bar = station("Bar")

        service.delete(venue.id, kitchen.id)

        assertEquals(listOf(bar.id), service.list(venue.id).map { it.id })
        assertEquals(0, stations.stored.getValue(bar.id).sortOrder)
    }

    @Test
    fun `an unknown station is not found`() {
        assertFailsWith<NotFoundException> { service.delete(venue.id, StationId.new()) }
    }
}