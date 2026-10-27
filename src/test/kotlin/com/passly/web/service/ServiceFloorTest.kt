package com.passly.web.service

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.floor.UpdateTableCommand
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
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.user.Role
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ServiceFloorTest {

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
    lateinit var waiter: RequestPostProcessor
    lateinit var seven: DiningTable
    lateinit var eight: DiningTable

    private val venueId get() = VenueId(venue.venueId)
    private val floor get() = "/service/venues/${venue.venueId}"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        waiterId = jdbc.createMember(venue, Role.WAITER)
        waiter = user(userDetailsService.loadUserByUsername("$waiterId@test.passly"))
        val terrace = floors.createArea(CreateAreaCommand(venueId, "Terrace"))
        seven = floors.createTable(CreateTableCommand(venueId, terrace.id, "7", 4))
        eight = floors.createTable(CreateTableCommand(venueId, terrace.id, "8", 2))
    }

    private fun openSevenWithRibeye() {
        val mains = categories.create(
            CreateCategoryCommand(
                venueId,
                "Mains",
                stationId = StationId(jdbc.createStation(venue))
            )
        )
        val ribeye = items.create(
            CreateMenuItemCommand(
                venueId,
                mains.id,
                MenuItemDetails("Ribeye", null, Money.of("26.00"))
            ),
        )
        orders.open(
            OpenOrderCommand(
                venueId,
                seven.id,
                UserId(waiterId),
                guests = 2,
                lines = listOf(OrderLineRequest(ribeye.id, 2, comment = "No salt")),
            ),
        )
    }

    @Test
    fun `floor shows busy tables with their total and free tables with seats`() {
        openSevenWithRibeye()

        mockMvc.get(floor) { with(waiter) }.andExpect {
            status { isOk() }
            content { string(containsString("1 of 2 tables busy")) }
            content { string(containsString("Terrace")) }
            content { string(containsString("52.00")) }
            content { string(containsString("2 guests")) }
            content { string(containsString("Free · 2 seats")) }
        }
    }

    @Test
    fun `inactive free table is hidden from the floor`() {
        floors.updateTable(UpdateTableCommand(venueId, eight.id, eight.areaId, "8", 2, active = false))

        mockMvc.get(floor) { with(waiter) }.andExpect {
            content { string(containsString("0 of 1 tables busy")) }
            content { string(doesNotContain("/tables/${eight.id.value}")) }
        }
    }

    @Test
    fun `table page lists the rounds of the open order`() {
        openSevenWithRibeye()

        mockMvc.get("$floor/tables/${seven.id.value}") { with(waiter) }.andExpect {
            status { isOk() }
            view { name("service/table") }
            content { string(containsString("Table 7")) }
            content { string(containsString("Round 1")) }
            content { string(containsString("Ribeye")) }
            content { string(containsString("No salt")) }
            content { string(containsString("52.00")) }
        }
    }

    @Test
    fun `free table page offers to open the table`() {
        mockMvc.get("$floor/tables/${eight.id.value}") { with(waiter) }.andExpect {
            status { isOk() }
            content { string(containsString("New order")) }
            content { string(containsString("Open table")) }
        }
    }

    @Test
    fun `table of another venue is not found`() {
        val other = jdbc.createVenue()
        val hall = floors.createArea(CreateAreaCommand(VenueId(other.venueId), "Hall"))
        val foreign = floors.createTable(CreateTableCommand(VenueId(other.venueId), hall.id, "1", 2))

        mockMvc.get("$floor/tables/${foreign.id.value}") { with(waiter) }.andExpect { status { isNotFound() } }
    }
}
