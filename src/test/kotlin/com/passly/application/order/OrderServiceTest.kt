package com.passly.application.order

import com.passly.application.floor.FakeFloorPlanRepository
import com.passly.application.menu.FakeCategoryRepository
import com.passly.application.menu.FakeMenuItemRepository
import com.passly.application.menu.FakeModifierGroupRepository
import com.passly.application.menu.FakeVenueRepository
import com.passly.application.tax.FakeTaxRateRepository
import com.passly.application.venue.FakeVenueSettingsRepository
import com.passly.domain.common.*
import com.passly.domain.floor.DiningTable
import com.passly.domain.menu.*
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.OrderStatus
import com.passly.domain.order.PaymentMethod
import com.passly.domain.order.Tender
import com.passly.domain.tax.VenueTaxRateId
import com.passly.domain.user.UserId
import com.passly.domain.venue.*
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
    private val settings = FakeVenueSettingsRepository(
        VenueSettings.fromStorage(venue.id, CountryCode.of("CH"), CurrencyCode.of("CHF"), version = 0)
            .apply { acceptCurrency(CurrencyCode.of("EUR"), ExchangeRate.of("0.95"), now) },
    )
    private val taxRates = FakeTaxRateRepository()
    private val food = taxRates.load(venue.id).add(venue.tenantId, "Food", TaxRate.of("10"))
        .also { taxRates.saveAll(listOf(it)) }
    private val alcohol = taxRates.load(venue.id).add(venue.tenantId, "Alcohol", TaxRate.of("20"))
        .also { taxRates.saveAll(listOf(it)) }
    private val service = OrderService(
        venues, floors, orders, items, groups, categories, settings, taxRates, Clock.fixed(now, ZoneOffset.UTC),
    )

    private val table: DiningTable = floors.load(venue.id).let { plan ->
        val hall = plan.addArea(venue.tenantId, "Hall")
        val table = plan.addTable(venue.tenantId, hall.id, "7", 4)
        floors.saveAreas(listOf(hall))
        floors.saveTables(listOf(table))
        table
    }

    private val mains = categories.loadTree(venue.id).add(venue.tenantId, "Mains", stationId = kitchen, taxRateId = food.id)
        .also { categories.saveAll(listOf(it)) }

    private fun item(name: String, price: String, taxRateId: VenueTaxRateId? = null): MenuItem =
        items.loadCategoryItems(venue.id, mains.id)
            .add(venue.tenantId, MenuItemDetails(name, null, Money.of(price), taxRateId = taxRateId))
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
    fun `full payment locks the order, closes it and frees the table`() {
        val coffee = item("Espresso", "1.80")
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter, lines = listOf(line(coffee, 2))))

        service.pay(PayOrderCommand(venue.id, order.id, waiter, PaymentMethod.CASH, Money.of("1.60")))
        service.pay(PayOrderCommand(venue.id, order.id, waiter, PaymentMethod.CARD, Money.of("2.00")))

        val stored = orders.stored.getValue(order.id)
        assertEquals(OrderStatus.CLOSED, stored.status)
        assertEquals(listOf(Money.of("1.60"), Money.of("2.00")), stored.payments.map { it.amount })
        assertEquals(now, stored.closedAt)
        assertEquals(2, orders.locks)
        assertEquals(null, orders.findOpenAtTable(table.id))
    }

    @Test
    fun `cash in an accepted currency is converted at the venue rate and change is in the main currency`() {
        val coffee = item("Espresso", "4.50")
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter, lines = listOf(line(coffee, 10))))

        service.pay(PayOrderCommand(venue.id, order.id, waiter, PaymentMethod.CASH, Money.of("50.00"), CurrencyCode.of("EUR")))

        val payment = orders.stored.getValue(order.id).payments.single()
        assertEquals(Money.of("45.00"), payment.amount)
        assertEquals(Tender(Money.of("50.00"), CurrencyCode.of("EUR"), ExchangeRate.of("0.95")), payment.tender)
        assertEquals(Money.of("2.50"), payment.change)
        assertEquals(OrderStatus.CLOSED, orders.stored.getValue(order.id).status)
    }

    @Test
    fun `payment in the main currency keeps no rate`() {
        val coffee = item("Espresso", "4.50")
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter, lines = listOf(line(coffee))))

        service.pay(PayOrderCommand(venue.id, order.id, waiter, PaymentMethod.CARD, Money.of("2.00")))

        assertEquals(Tender.main(Money.of("2.00"), CurrencyCode.of("CHF")), orders.stored.getValue(order.id).payments.single().tender)
    }

    @Test
    fun `currency the venue does not accept is refused`() {
        val coffee = item("Espresso", "4.50")
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter, lines = listOf(line(coffee))))

        val error = assertFailsWith<ValidationException> {
            service.pay(PayOrderCommand(venue.id, order.id, waiter, PaymentMethod.CASH, Money.of("5.00"), CurrencyCode.of("USD")))
        }
        assertEquals("currency", error.field)
        assertTrue(orders.stored.getValue(order.id).payments.isEmpty())
    }

    @Test
    fun `an order opened by mistake is closed without payment`() {
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter))

        service.close(CloseOrderCommand(venue.id, order.id, waiter))

        assertEquals(OrderStatus.CLOSED, orders.stored.getValue(order.id).status)
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

    @Test
    fun `lines keep the rate of the category or the own rate of the item`() {
        val burger = item("Burger", "14.00")
        val beer = item("Beer", "5.00", taxRateId = alcohol.id)

        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter, guests = 2, lines = listOf(line(burger), line(beer))))

        assertEquals(listOf(TaxRate.of("10"), TaxRate.of("20")), orders.stored.getValue(order.id).lines.map { it.taxRate })
    }
}
