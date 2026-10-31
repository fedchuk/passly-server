package com.passly.web.api.order

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.application.order.OpenOrderCommand
import com.passly.application.order.OrderChangeKind
import com.passly.application.order.OrderChangedEvent
import com.passly.application.order.OrderService
import com.passly.application.outbox.OutboxRelay
import com.passly.domain.common.Money
import com.passly.domain.floor.DiningTable
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.web.api.*
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OrderEventStreamTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var streams: OrderEventStreams

    @Autowired
    lateinit var relay: OutboxRelay

    @Autowired
    lateinit var floors: FloorPlanService

    @Autowired
    lateinit var categories: CategoryService

    @Autowired
    lateinit var items: MenuItemService

    @Autowired
    lateinit var orders: OrderService

    lateinit var venue: TestVenue
    lateinit var waiterId: UUID
    lateinit var waiter: MockMvc
    lateinit var stationId: UUID
    lateinit var table: DiningTable
    lateinit var steak: MenuItem

    private val venueId get() = VenueId(venue.venueId)

    @BeforeEach
    fun setUp() {
        venue = jdbc.createVenue()
        waiterId = jdbc.createMember(venue, StaffRole.WAITER)
        waiter = context.mockMvcAs(waiterId)
        stationId = jdbc.createStation(venue)
        val mains = categories.create(CreateCategoryCommand(venueId, "Mains", stationId = StationId(stationId)))
        steak = items.create(CreateMenuItemCommand(venueId, mains.id, MenuItemDetails("Steak", null, Money.of("28.00"))))
        table = floors.createTable(CreateTableCommand(venueId, floors.createArea(CreateAreaCommand(venueId, "Hall")).id, "7", 4))
    }

    private fun openStream(client: MockMvc = waiter, path: String = "orders/events"): MockHttpServletResponse =
        client.get("/api/v1/venues/${venue.venueId}/$path") { accept(MediaType.TEXT_EVENT_STREAM) }
            .andExpect { request { asyncStarted() } }
            .andReturn()
            .response

    @Test
    fun `waiter stream receives changes of its venue only`() {
        val stream = openStream()
        val orderId = UUID.randomUUID()

        streams.orderChanged(event(venue.venueId, orderId))
        streams.orderChanged(event(UUID.randomUUID(), UUID.randomUUID()))

        val body = stream.contentAsString
        assertTrue(body.contains("event:order"))
        assertTrue(body.contains("\"orderId\":\"$orderId\""))
        assertTrue(body.contains("\"change\":\"ROUND_SENT\""))
        assertTrue(body.contains("\"stationIds\":[\"$stationId\"]"))
        assertEquals(1, body.split("event:order").size - 1)
    }

    @Test
    fun `a sent round goes through the outbox to the kitchen stream`() {
        val stream = openStream(context.mockMvcAs(jdbc.createMember(venue, StaffRole.KITCHEN)), "kitchen/events")

        val order = orders.open(OpenOrderCommand(venueId, table.id, UserId(waiterId), lines = listOf(OrderLineRequest(steak.id, 1))))
        val stored = jdbc.sql("select event_type, topic from outbox_event where aggregate_type = 'Order' and aggregate_id = ?")
            .param(order.id.value)
            .query { rs, _ -> rs.getString("event_type") to rs.getString("topic") }
            .single()
        relay.relayBatch(500)

        assertEquals(OrderChangedEvent.TYPE to "passly.order-events", stored)
        assertTrue(eventually { stream.contentAsString.contains("\"orderId\":\"${order.id}\"") })
    }

    @Test
    fun `kitchen cannot listen to the waiter stream`() {
        context.mockMvcAs(jdbc.createMember(venue, StaffRole.KITCHEN))
            .get("/api/v1/venues/${venue.venueId}/orders/events") { accept(MediaType.TEXT_EVENT_STREAM) }
            .andExpect { status { isForbidden() } }
    }

    private fun eventually(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + 5_000_000_000
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    private fun event(venueUuid: UUID, orderId: UUID) = OrderChangedEvent(
        eventId = UUID.randomUUID().toString(),
        type = OrderChangedEvent.TYPE,
        venueId = venueUuid.toString(),
        orderId = orderId.toString(),
        tableId = table.id.value.toString(),
        change = OrderChangeKind.ROUND_SENT,
        stationIds = listOf(stationId.toString()),
        occurredAt = Instant.now().toString(),
    )
}
