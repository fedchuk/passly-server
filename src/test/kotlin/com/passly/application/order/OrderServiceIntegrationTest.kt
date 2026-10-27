package com.passly.application.order

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.menu.*
import com.passly.domain.common.ConflictException
import com.passly.domain.common.Money
import com.passly.domain.common.UnavailableItemsException
import com.passly.domain.floor.DiningTable
import com.passly.domain.menu.*
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.OrderStatus
import com.passly.domain.order.PaymentMethod
import com.passly.domain.user.Role
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.web.api.TestVenue
import com.passly.web.api.createMember
import com.passly.web.api.createStation
import com.passly.web.api.createVenue
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OrderServiceIntegrationTest {

    @Autowired
    lateinit var orders: OrderService

    @Autowired
    lateinit var floors: FloorPlanService

    @Autowired
    lateinit var categories: CategoryService

    @Autowired
    lateinit var items: MenuItemService

    @Autowired
    lateinit var modifierGroups: ModifierGroupService

    @Autowired
    lateinit var jdbc: JdbcClient

    lateinit var venue: TestVenue
    lateinit var waiterUuid: UUID
    lateinit var table: DiningTable
    lateinit var mainsUuid: UUID

    private val venueId get() = VenueId(venue.venueId)
    private val waiter get() = UserId(waiterUuid)

    @BeforeEach
    fun setUp() {
        venue = jdbc.createVenue()
        waiterUuid = jdbc.createMember(venue, Role.WAITER)
        val hall = floors.createArea(CreateAreaCommand(venueId, "Hall"))
        table = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4))
        mainsUuid = categories.create(CreateCategoryCommand(venueId, "Mains", stationId = StationId(jdbc.createStation(venue)))).id.value
    }

    private fun item(name: String, price: String): MenuItem = items.create(
        CreateMenuItemCommand(
            venueId,
            CategoryId(mainsUuid),
            MenuItemDetails(name, null, Money.of(price))
        ),
    )

    @Test
    fun `order with rounds and modifiers survives a round trip`() {
        val steak = item("Ribeye", "26.00")
        val coffee = item("Espresso", "1.80")
        val doneness = modifierGroups.create(
            CreateModifierGroupCommand(
                venueId, "Doneness", 1, 1,
                listOf(ModifierDraft("Rare", Money.of("0")), ModifierDraft("Well done", Money.of("0.50"))),
            ),
        )
        items.attachModifierGroups(AttachModifierGroupsCommand(venueId, steak.id, listOf(doneness.id)))
        val wellDone = doneness.modifiers[1].id

        val opened = orders.open(
            OpenOrderCommand(
                venueId, table.id, waiter, guests = 2,
                lines = listOf(OrderLineRequest(steak.id, 1, listOf(wellDone), "sauce on the side"), OrderLineRequest(coffee.id, 2)),
            ),
        )
        orders.addRound(AddRoundCommand(venueId, opened.id, waiter, listOf(OrderLineRequest(coffee.id, 1))))

        val order = orders.get(venueId, opened.id)
        assertEquals(listOf("Ribeye", "Espresso", "Espresso"), order.lines.map { it.name })
        assertEquals(listOf(1, 1, 2), order.lines.map { it.round })
        assertEquals("Doneness: Well done", order.lines[0].modifiers.single().let { "${it.groupName}: ${it.name}" })
        assertEquals(Money.of("26.50"), order.lines[0].unitPrice)
        assertEquals("sauce on the side", order.lines[0].comment)
        assertEquals(Money.of("31.90"), order.total)
        assertEquals(2, order.guests)
    }

    @Test
    fun `snapshot keeps the price the guest ordered at`() {
        val coffee = item("Espresso", "1.80")
        val order = orders.open(OpenOrderCommand(venueId, table.id, waiter, lines = listOf(OrderLineRequest(coffee.id, 1))))

        jdbc.sql("update menu_item set price = 2.50, name = 'Espresso doppio' where id = ?").params(coffee.id.value).update()

        val line = orders.get(venueId, order.id).lines.single()
        assertEquals(Money.of("1.80"), line.unitPrice)
        assertEquals("Espresso", line.name)
    }

    @Test
    fun `a stop-listed item rejects the round and stores nothing`() {
        val salmon = item("Salmon", "19.00")
        items.changeAvailability(venueId, salmon.id, available = false)
        val order = orders.open(OpenOrderCommand(venueId, table.id, waiter))

        val error = assertFailsWith<UnavailableItemsException> {
            orders.addRound(AddRoundCommand(venueId, order.id, waiter, listOf(OrderLineRequest(salmon.id, 1))))
        }

        assertEquals(listOf(salmon.id.value), error.itemIds)
        assertTrue(orders.get(venueId, order.id).lines.isEmpty())
    }

    @Test
    fun `one open order per table is enforced by the service and by the database`() {
        val order = orders.open(OpenOrderCommand(venueId, table.id, waiter))

        assertFailsWith<ConflictException> { orders.open(OpenOrderCommand(venueId, table.id, waiter)) }
        assertFailsWith<DataIntegrityViolationException> {
            jdbc.sql(
                "insert into orders (id, tenant_id, venue_id, table_id, status, opened_by, opened_at) values (?, ?, ?, ?, 'OPEN', ?, now())",
            ).params(UUID.randomUUID(), venue.tenantId, venue.venueId, table.id.value, waiterUuid).update()
        }
        assertEquals(listOf(order.id), orders.listOpen(venueId).map { it.id })
    }

    @Test
    fun `a table with orders is not deleted`() {
        orders.open(OpenOrderCommand(venueId, table.id, waiter))

        assertFailsWith<ConflictException> { floors.deleteTable(venueId, table.id) }
        assertEquals(listOf("7"), floors.get(venueId).tables.map { it.label })
    }

    @Test
    fun `payments survive a round trip and the table can be opened again`() {
        val coffee = item("Espresso", "1.80")
        val order = orders.open(OpenOrderCommand(venueId, table.id, waiter, lines = listOf(OrderLineRequest(coffee.id, 3))))

        orders.pay(PayOrderCommand(venueId, order.id, waiter, PaymentMethod.CASH, Money.of("2.00")))
        orders.pay(PayOrderCommand(venueId, order.id, waiter, PaymentMethod.CARD, Money.of("3.40")))

        val closed = orders.get(venueId, order.id)
        assertEquals(OrderStatus.CLOSED, closed.status)
        assertEquals(listOf(PaymentMethod.CASH, PaymentMethod.CARD), closed.payments.map { it.method })
        assertEquals(Money.of("5.40"), closed.paid)
        assertEquals(waiter, closed.closedBy)
        assertTrue(orders.listOpen(venueId).isEmpty())

        val next = orders.open(OpenOrderCommand(venueId, table.id, waiter))
        assertEquals(listOf(next.id), orders.listOpen(venueId).map { it.id })
    }

    @Test
    fun `a closed order without closing details is rejected by the database`() {
        val order = orders.open(OpenOrderCommand(venueId, table.id, waiter))

        assertFailsWith<DataIntegrityViolationException> {
            jdbc.sql("update orders set status = 'CLOSED' where id = ?").params(order.id.value).update()
        }
    }
}
