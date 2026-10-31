package com.passly.web.service

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.kitchen.KitchenService
import com.passly.application.kitchen.MoveLinesCommand
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.application.order.OpenOrderCommand
import com.passly.application.order.OrderService
import com.passly.domain.common.Money
import com.passly.domain.floor.DiningTable
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.PrepStatus
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.web.api.*
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ServiceServeTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var floors: FloorPlanService

    @Autowired
    lateinit var categories: CategoryService

    @Autowired
    lateinit var items: MenuItemService

    @Autowired
    lateinit var orders: OrderService

    @Autowired
    lateinit var kitchen: KitchenService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var waiterId: UUID
    lateinit var waiter: RequestPostProcessor
    lateinit var table: DiningTable
    lateinit var order: Order

    private val venueId get() = VenueId(venue.venueId)
    private val floor get() = "/service/venues/${venue.venueId}"
    private val tablePage get() = "$floor/tables/${table.id.value}"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        waiterId = jdbc.createMember(venue, StaffRole.WAITER)
        waiter = user(userDetailsService.loadUserByUsername("$waiterId@test.passly"))
        val mains = categories.create(CreateCategoryCommand(venueId, "Plats", stationId = StationId(jdbc.createStation(venue))))
        val steak = items.create(CreateMenuItemCommand(venueId, mains.id, MenuItemDetails("Entrecôte", null, Money.of("28.00"))))
        val salad = items.create(CreateMenuItemCommand(venueId, mains.id, MenuItemDetails("Salade", null, Money.of("9.00"))))
        table = floors.createTable(CreateTableCommand(venueId, floors.createArea(CreateAreaCommand(venueId, "Hall")).id, "7", 4))
        order = orders.open(
            OpenOrderCommand(venueId, table.id, UserId(waiterId), lines = listOf(OrderLineRequest(steak.id, 1), OrderLineRequest(salad.id, 1))),
        )
        kitchen.moveLines(MoveLinesCommand(venueId, order.id, listOf(order.lines.first().id), PrepStatus.READY))
    }

    private fun stored(): Order = orders.get(venueId, order.id)

    @Test
    fun `floor and table show what is ready to take`() {
        mockMvc.get(floor) { with(waiter) }.andExpect { content { string(containsString("1 ready")) } }
        mockMvc.get(tablePage) { with(waiter) }.andExpect {
            content { string(containsString("1 ready to take to the table")) }
            content { string(containsString("<span class=\"prep-pill ready\">Ready</span>")) }
            content { string(containsString("<span class=\"prep-pill new\">Sent</span>")) }
            content { string(containsString("data-table-id=\"${table.id.value}\"")) }
        }
    }

    @Test
    fun `waiter serves the ready lines`() {
        mockMvc.post("$tablePage/served") {
            with(waiter)
            with(csrf())
            param("orderId", order.id.value.toString())
            param("lineId", order.lines.first().id.value.toString())
        }.andExpect {
            redirectedUrl(tablePage)
            flash { attribute("message", "Entrecôte served") }
        }

        assertEquals(listOf(PrepStatus.SERVED, PrepStatus.NEW), stored().lines.map { it.status })
    }

    @Test
    fun `api marks lines as served`() {
        context.mockMvcAs(waiterId).post("/api/v1/venues/${venue.venueId}/orders/${order.id}/served") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"lineIds": ["${order.lines.first().id}"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.lines[0].status") { value("SERVED") }
            jsonPath("$.lines[1].status") { value("NEW") }
        }
    }

    @Test
    fun `screens subscribe to live order events`() {
        mockMvc.get("$floor/events") {
            with(waiter)
            accept(MediaType.TEXT_EVENT_STREAM)
        }.andExpect { request { asyncStarted() } }

        val cook = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, StaffRole.KITCHEN)}@test.passly"))
        mockMvc.get("/kitchen/venues/${venue.venueId}/events") {
            with(cook)
            accept(MediaType.TEXT_EVENT_STREAM)
        }.andExpect { request { asyncStarted() } }
        mockMvc.get("$floor/events") {
            with(cook)
            accept(MediaType.TEXT_EVENT_STREAM)
        }.andExpect { status { isForbidden() } }
    }
}
