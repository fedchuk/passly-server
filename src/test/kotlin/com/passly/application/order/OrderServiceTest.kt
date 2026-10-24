package com.passly.application.order

import com.passly.application.floor.FakeFloorPlanRepository
import com.passly.application.menu.FakeCategoryRepository
import com.passly.application.menu.FakeMenuItemRepository
import com.passly.application.menu.FakeModifierGroupRepository
import com.passly.application.menu.FakeVenueRepository
import com.passly.domain.common.*
import com.passly.domain.floor.DiningTable
import com.passly.domain.menu.*
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.user.UserId
import com.passly.domain.venue.TenantId
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OrderServiceTest {

    private val venue = Venue(VenueId.new(), TenantId.new(), "Bistro")
    private val kitchen = StationId.new()
    private val waiter = UserId.new()
    private val now = Instant.parse("2026-10-24T19:30:00Z")

    private val venues = FakeVenueRepository(venue)
    private val floors = FakeFloorPlanRepository()
    private val orders = FakeOrderRepository()
    private val items = FakeMenuItemRepository()
    private val groups = FakeModifierGroupRepository()
    private val categories = FakeCategoryRepository()
    private val service = OrderService(
        venues, floors, orders, items, groups, categories, Clock.fixed(now, ZoneOffset.UTC),
    )

    private val table: DiningTable = floors.load(venue.id).let { plan ->
        val hall = plan.addArea(venue.tenantId, "Hall")
        val table = plan.addTable(venue.tenantId, hall.id, "7", 4)
        floors.saveAreas(listOf(hall))
        floors.saveTables(listOf(table))
        table
    }

    private val mains = categories.loadTree(venue.id).add(venue.tenantId, "Mains", stationId = kitchen)
        .also { categories.saveAll(listOf(it)) }

    private fun item(name: String, price: String): MenuItem =
        items.loadCategoryItems(venue.id, mains.id)
            .add(venue.tenantId, MenuItemDetails(name, null, Money.of(price), TaxRate.of("10")))
            .also { items.saveAll(listOf(it)) }

    private fun line(item: MenuItem, quantity: Int = 1) = OrderLineRequest(item.id, quantity)

    @Test
    fun `opening with lines stores the first round with prices and the station of the category`() {
        val burger = item("Burger", "14.00")

        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter, guests = 2, lines = listOf(line(burger, 2))))

        val stored = orders.stored.getValue(order.id)
        assertEquals(1, stored.rounds)
        assertEquals(Money.of("28.00"), stored.total)
        assertEquals(kitchen, stored.lines.single().stationId)
        assertEquals(now, stored.lines.single().addedAt)
        assertEquals(waiter, stored.openedBy)
    }

    @Test
    fun `a table with an open order cannot be opened again`() {
        service.open(OpenOrderCommand(venue.id, table.id, waiter))

        assertFailsWith<ConflictException> { service.open(OpenOrderCommand(venue.id, table.id, waiter)) }
        assertEquals(1, orders.stored.size)
    }

    @Test
    fun `stop-listed and removed items reject the whole round`() {
        val burger = item("Burger", "14.00")
        val salmon = item("Salmon", "19.00")
        items.saveAll(listOf(items.findActive(venue.id, salmon.id)!!.apply { addToStopList() }))
        val removed = MenuItemId.new()
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter))

        val error = assertFailsWith<UnavailableItemsException> {
            service.addRound(
                AddRoundCommand(venue.id, order.id, waiter, listOf(line(burger), line(salmon), OrderLineRequest(removed, 1))),
            )
        }

        assertEquals(setOf(salmon.id.value, removed.value), error.itemIds.toSet())
        assertTrue(orders.stored.getValue(order.id).lines.isEmpty())
    }

    @Test
    fun `next round locks the order and keeps earlier lines`() {
        val coffee = item("Espresso", "1.80")
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter, lines = listOf(line(coffee))))

        service.addRound(AddRoundCommand(venue.id, order.id, waiter, listOf(line(coffee, 2))))

        val stored = orders.stored.getValue(order.id)
        assertEquals(listOf(1, 2), stored.lines.map { it.round })
        assertEquals(Money.of("5.40"), stored.total)
        assertEquals(1, orders.locks)
    }

    @Test
    fun `modifiers are priced from the venue groups`() {
        val steak = item("Ribeye", "26.00")
        val doneness = ModifierGroup.create(
            venue.tenantId, venue.id, "Doneness", 1, 1,
            listOf(ModifierDraft("Rare", Money.of("0")), ModifierDraft("Wagyu upgrade", Money.of("12.00"))),
        ).also { groups.save(it) }
        items.saveAll(listOf(items.findActive(venue.id, steak.id)!!.apply { attachModifierGroups(listOf(doneness.id)) }))

        val order = service.open(
            OpenOrderCommand(
                venue.id, table.id, waiter,
                lines = listOf(OrderLineRequest(steak.id, 1, listOf(doneness.modifiers[1].id))),
            ),
        )

        assertEquals(Money.of("38.00"), orders.stored.getValue(order.id).total)
    }

    @Test
    fun `a table of another venue and an order of another venue are not found`() {
        val other = Venue(VenueId.new(), venue.tenantId, "Other")
        venues.save(other)
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter))

        assertFailsWith<NotFoundException> { service.open(OpenOrderCommand(other.id, table.id, waiter)) }
        assertFailsWith<NotFoundException> { service.get(other.id, order.id) }
    }
}
