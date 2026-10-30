package com.passly.web.kitchen

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.application.order.AddRoundCommand
import com.passly.application.order.OpenOrderCommand
import com.passly.application.order.OrderService
import com.passly.application.order.PayOrderCommand
import com.passly.domain.common.Money
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.PaymentMethod
import com.passly.domain.order.PrepStatus
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.web.api.*
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
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
class KitchenStationTest {

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

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var waiterId: UUID
    lateinit var cook: RequestPostProcessor
    lateinit var kitchenId: UUID
    lateinit var barId: UUID
    lateinit var steak: MenuItem
    lateinit var wine: MenuItem
    lateinit var order: Order

    private val venueId get() = VenueId(venue.venueId)
    private val base get() = "/kitchen/venues/${venue.venueId}"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        waiterId = jdbc.createMember(venue, StaffRole.WAITER)
        cook = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, StaffRole.KITCHEN)}@test.passly"))
        kitchenId = jdbc.createStation(venue, "Hot kitchen")
        barId = jdbc.createStation(venue, "Main bar")
        val mains = categories.create(CreateCategoryCommand(venueId, "Plats", stationId = StationId(kitchenId)))
        val drinks = categories.create(CreateCategoryCommand(venueId, "Vins", stationId = StationId(barId)))
        steak = items.create(CreateMenuItemCommand(venueId, mains.id, MenuItemDetails("Entrecôte", null, Money.of("28.00"))))
        wine = items.create(CreateMenuItemCommand(venueId, drinks.id, MenuItemDetails("Bordeaux", null, Money.of("6.50"))))
        val hall = floors.createArea(CreateAreaCommand(venueId, "Hall"))
        val table = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4))
        order = orders.open(
            OpenOrderCommand(
                venueId, table.id, UserId(waiterId),
                lines = listOf(OrderLineRequest(steak.id, 2, comment = "No salt"), OrderLineRequest(wine.id, 2)),
            ),
        )
    }

    private fun lineOf(item: MenuItem) = orders.get(venueId, order.id).lines.first { it.menuItemId == item.id }

    private fun move(stationId: UUID, status: String, vararg lineIds: UUID) = mockMvc.post("$base/stations/$stationId/lines") {
        with(cook)
        with(csrf())
        param("orderId", order.id.value.toString())
        lineIds.forEach { param("lineId", it.toString()) }
        param("status", status)
    }

    @Test
    fun `screen starts with a choice of stations`() {
        mockMvc.get(base) { with(cook) }.andExpect {
            status { isOk() }
            view { name("kitchen/board") }
            content { string(containsString("$base/stations/$kitchenId")) }
            content { string(containsString("Main bar")) }
        }
    }

    @Test
    fun `each station sees only its part of the round`() {
        mockMvc.get("$base/stations/$kitchenId") { with(cook) }.andExpect {
            status { isOk() }
            content { string(containsString("Entrecôte")) }
            content { string(containsString("No salt")) }
            content { string(containsString("Table 7")) }
            content { string(doesNotContain("Bordeaux")) }
        }
        mockMvc.get("$base/stations/$barId") { with(cook) }.andExpect {
            content { string(containsString("Bordeaux")) }
            content { string(doesNotContain("Entrecôte")) }
        }
    }

    @Test
    fun `cook starts, finishes and hands over a ticket`() {
        val line = lineOf(steak).id.value

        move(kitchenId, "IN_PROGRESS", line).andExpect { redirectedUrl("$base/stations/$kitchenId") }
        assertEquals(PrepStatus.IN_PROGRESS, lineOf(steak).status)

        move(kitchenId, "READY", line)
        mockMvc.get("$base/stations/$kitchenId") { with(cook) }.andExpect {
            content { string(containsString("1 ready for pickup")) }
            content { string(containsString("All picked up")) }
        }

        move(kitchenId, "SERVED", line)
        mockMvc.get("$base/stations/$kitchenId") { with(cook) }.andExpect {
            content { string(containsString("Nothing to prepare.")) }
            content { string(containsString("Nothing waiting.")) }
        }
        assertEquals(PrepStatus.NEW, lineOf(wine).status)
    }

    @Test
    fun `next round becomes its own ticket and a paid order stays on the board`() {
        orders.addRound(AddRoundCommand(venueId, order.id, UserId(waiterId), listOf(OrderLineRequest(steak.id, 1))))
        orders.pay(PayOrderCommand(venueId, order.id, UserId(waiterId), PaymentMethod.CARD, Money.of("97.00")))

        mockMvc.get("$base/stations/$kitchenId") { with(cook) }.andExpect {
            content { string(containsString("2 to prepare")) }
            content { string(containsString("Round 2")) }
        }
    }

    @Test
    fun `unknown line is reported and waiter cannot use the screen`() {
        move(kitchenId, "READY", UUID.randomUUID()).andExpect { flash { attribute("error", containsString("not found")) } }

        val waiter = user(userDetailsService.loadUserByUsername("$waiterId@test.passly"))
        mockMvc.get("$base/stations/$kitchenId") { with(waiter) }.andExpect { status { isForbidden() } }
    }
}
