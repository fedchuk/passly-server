package com.passly.web.api.kitchen

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.application.order.OpenOrderCommand
import com.passly.application.order.OrderService
import com.passly.domain.common.Money
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class KitchenApiTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

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
    lateinit var cook: MockMvc
    lateinit var barId: UUID
    lateinit var order: Order

    private val venueId get() = VenueId(venue.venueId)
    private val base get() = "/api/v1/venues/${venue.venueId}/kitchen"

    @BeforeEach
    fun setUp() {
        venue = jdbc.createVenue()
        waiterId = jdbc.createMember(venue, StaffRole.WAITER)
        cook = context.mockMvcAs(jdbc.createMember(venue, StaffRole.KITCHEN))
        barId = jdbc.createStation(venue, "Main bar")
        val drinks = categories.create(CreateCategoryCommand(venueId, "Cocktails", stationId = StationId(barId)))
        val mojito = items.create(CreateMenuItemCommand(venueId, drinks.id, MenuItemDetails("Mojito", null, Money.of("9.00"))))
        val hall = floors.createArea(CreateAreaCommand(venueId, "Terrace"))
        val table = floors.createTable(CreateTableCommand(venueId, hall.id, "T3", 2))
        order = orders.open(OpenOrderCommand(venueId, table.id, UserId(waiterId), lines = listOf(OrderLineRequest(mojito.id, 2))))
    }

    private fun move(status: String, lineId: UUID = order.lines.single().id.value, client: MockMvc = cook) =
        client.post("$base/line-status") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderId": "${order.id}", "lineIds": ["$lineId"], "status": "$status"}"""
        }

    @Test
    fun `bar gets its ticket`() {
        cook.get("$base/stations/$barId/tickets").andExpect {
            status { isOk() }
            jsonPath("$[0].orderId") { value(order.id.toString()) }
            jsonPath("$[0].table") { value("T3") }
            jsonPath("$[0].round") { value(1) }
            jsonPath("$[0].status") { value("NEW") }
            jsonPath("$[0].lines[0].name") { value("Mojito") }
            jsonPath("$[0].lines[0].quantity") { value(2) }
        }
    }

    @Test
    fun `line moves forward once and the order shows it`() {
        move("READY").andExpect {
            status { isOk() }
            jsonPath("$[0].status") { value("READY") }
        }
        move("READY").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }

        context.mockMvcAs(waiterId).get("/api/v1/venues/${venue.venueId}/orders/${order.id}").andExpect {
            jsonPath("$.lines[0].status") { value("READY") }
        }
    }

    @Test
    fun `wrong requests are refused`() {
        move("NEW").andExpect { status { isBadRequest() } }
        move("READY", lineId = UUID.randomUUID()).andExpect { status { isNotFound() } }
        cook.get("$base/stations/${UUID.randomUUID()}/tickets").andExpect { status { isNotFound() } }
        move("READY", client = context.mockMvcAs(waiterId)).andExpect { status { isForbidden() } }
    }
}
