package com.passly.application.order

import com.passly.application.discount.FakeDiscountRepository
import com.passly.application.floor.FakeFloorPlanRepository
import com.passly.application.menu.FakeCategoryRepository
import com.passly.application.menu.FakeMenuItemRepository
import com.passly.application.menu.FakeModifierGroupRepository
import com.passly.application.menu.FakeVenueRepository
import com.passly.application.tax.FakeTaxRateRepository
import com.passly.application.venue.FakeVenueSettingsRepository
import com.passly.application.voucher.FakeVoucherRepository
import com.passly.domain.common.*
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.discount.VenueDiscountId
import com.passly.domain.voucher.Voucher
import com.passly.domain.voucher.VoucherCode
import com.passly.domain.voucher.VoucherKind
import com.passly.domain.voucher.VoucherValue
import java.time.LocalDate
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
    private val discounts = FakeDiscountRepository()
    private val vouchers = FakeVoucherRepository()
    private val changes = FakeOrderChangeRecorder()
    private val service = OrderService(
        venues, floors, orders, items, groups, categories, settings, taxRates, discounts, vouchers, changes, Clock.fixed(now, ZoneOffset.UTC),
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

    @Test
    fun `discount is applied from the venue list and kept on the order`() {
        val regular = discounts.load(venue.id).add(venue.tenantId, "Regular guest", DiscountPercent.of("10"))
            .also { discounts.saveAll(listOf(it)) }
        val steak = item("Steak", "28.00")
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter, lines = listOf(line(steak))))

        service.applyDiscount(ApplyDiscountCommand(venue.id, order.id, regular.id, waiter))

        val stored = orders.stored.getValue(order.id)
        assertEquals("Regular guest", stored.discount?.name)
        assertEquals(waiter, stored.discount?.appliedBy)
        assertEquals(now, stored.discount?.appliedAt)
        assertEquals(Money.of("25.20"), stored.due)
        assertEquals(1, orders.locks)

        service.removeDiscount(RemoveDiscountCommand(venue.id, order.id))

        assertEquals(Money.of("28.00"), orders.stored.getValue(order.id).due)
    }

    @Test
    fun `unknown discount is not found`() {
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter, lines = listOf(line(item("Steak", "28.00")))))

        assertFailsWith<NotFoundException> {
            service.applyDiscount(ApplyDiscountCommand(venue.id, order.id, VenueDiscountId.new(), waiter))
        }
    }

    private fun voucher(code: String, kind: VoucherKind, validUntil: String = "2026-12-31"): Voucher =
        Voucher.create(
            venue.tenantId, venue.id, VoucherCode.parse(code), "Gift", kind, VoucherValue.Amount(Money.of("10.00")),
            LocalDate.parse(validUntil), LocalDate.parse("2026-10-24"), now,
        ).also(vouchers::save)

    @Test
    fun `single-use voucher is held by the bill and freed when removed`() {
        val gift = voucher("ABCD-2345", VoucherKind.SINGLE_USE)
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter, lines = listOf(line(item("Steak", "28.00")))))

        service.applyVoucher(ApplyVoucherCommand(venue.id, order.id, VoucherCode.parse("abcd2345"), waiter))

        assertEquals(Money.of("18.00"), orders.stored.getValue(order.id).due)
        assertEquals(order.id, vouchers.stored.getValue(gift.id).redeemedOrderId)

        service.removeVoucher(RemoveVoucherCommand(venue.id, order.id))

        assertEquals(null, vouchers.stored.getValue(gift.id).redeemedOrderId)
        assertEquals(Money.of("28.00"), orders.stored.getValue(order.id).due)
    }

    @Test
    fun `voucher of another bill is refused and the last day still counts`() {
        voucher("ABCD-2345", VoucherKind.SINGLE_USE)
        voucher("WXYZ-6789", VoucherKind.REUSABLE, validUntil = "2026-10-24")
        val steak = item("Steak", "28.00")
        val first = service.open(OpenOrderCommand(venue.id, table.id, waiter, lines = listOf(line(steak))))
        service.applyVoucher(ApplyVoucherCommand(venue.id, first.id, VoucherCode.parse("ABCD-2345"), waiter))
        val other = floors.load(venue.id).let { plan ->
            plan.addTable(venue.tenantId, plan.areas.first().id, "8", 2).also { floors.saveTables(listOf(it)) }
        }
        val second = service.open(OpenOrderCommand(venue.id, other.id, waiter, lines = listOf(line(steak))))

        val used = assertFailsWith<ConflictException> {
            service.applyVoucher(ApplyVoucherCommand(venue.id, second.id, VoucherCode.parse("ABCD-2345"), waiter))
        }
        assertEquals("Voucher ABCD-2345 was already used", used.message)

        service.applyVoucher(ApplyVoucherCommand(venue.id, second.id, VoucherCode.parse("WXYZ-6789"), waiter))
        assertFailsWith<NotFoundException> {
            service.applyVoucher(ApplyVoucherCommand(venue.id, second.id, VoucherCode.parse("QQQQ-2222"), waiter))
        }
    }

    @Test
    fun `replacing a single-use voucher frees the previous one`() {
        val first = voucher("ABCD-2345", VoucherKind.SINGLE_USE)
        val second = voucher("WXYZ-6789", VoucherKind.SINGLE_USE)
        val order = service.open(OpenOrderCommand(venue.id, table.id, waiter, lines = listOf(line(item("Steak", "28.00")))))

        service.applyVoucher(ApplyVoucherCommand(venue.id, order.id, first.code, waiter))
        service.applyVoucher(ApplyVoucherCommand(venue.id, order.id, second.code, waiter))

        assertEquals(null, vouchers.stored.getValue(first.id).redeemedOrderId)
        assertEquals(order.id, vouchers.stored.getValue(second.id).redeemedOrderId)
        assertEquals(second.id, orders.stored.getValue(order.id).voucher?.voucherId)
    }

    @Test
    fun `every change of an order is recorded with the stations it concerns`() {
        val steak = item("Steak", "28.00")
        val empty = service.open(OpenOrderCommand(venue.id, table.id, waiter))

        service.addRound(AddRoundCommand(venue.id, empty.id, waiter, listOf(line(steak))))
        service.pay(PayOrderCommand(venue.id, empty.id, waiter, PaymentMethod.CARD, Money.of("10.00")))
        service.pay(PayOrderCommand(venue.id, empty.id, waiter, PaymentMethod.CARD, Money.of("18.00")))

        assertEquals(
            listOf(OrderChangeKind.OPENED, OrderChangeKind.ROUND_SENT, OrderChangeKind.PAID, OrderChangeKind.CLOSED),
            changes.kinds(),
        )
        assertEquals(setOf(kitchen), changes.recorded[1].third)
    }
}
