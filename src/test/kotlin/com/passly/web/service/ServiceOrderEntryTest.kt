package com.passly.web.service

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.menu.*
import com.passly.application.order.FloorStatusQuery
import com.passly.application.order.OpenOrderCommand
import com.passly.application.order.OrderService
import com.passly.domain.common.Money
import com.passly.domain.floor.DiningTable
import com.passly.domain.menu.*
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
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
import kotlin.test.assertNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ServiceOrderEntryTest {

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
    lateinit var modifierGroups: ModifierGroupService

    @Autowired
    lateinit var orders: OrderService

    @Autowired
    lateinit var floorStatus: FloorStatusQuery

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var waiterId: UUID
    lateinit var waiter: RequestPostProcessor
    lateinit var table: DiningTable
    lateinit var ribeye: MenuItem
    lateinit var water: MenuItem
    lateinit var doneness: ModifierGroup

    private val venueId get() = VenueId(venue.venueId)
    private val tablePage get() = "/service/venues/${venue.venueId}/tables/${table.id.value}"
    private val rounds get() = "$tablePage/rounds"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        waiterId = jdbc.createMember(venue, StaffRole.WAITER)
        waiter = user(userDetailsService.loadUserByUsername("$waiterId@test.passly"))
        val hall = floors.createArea(CreateAreaCommand(venueId, "Hall"))
        table = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4))

        val mains = categories.create(
            CreateCategoryCommand(
                venueId,
                "Mains",
                stationId = StationId(jdbc.createStation(venue))
            )
        )
        ribeye = items.create(
            CreateMenuItemCommand(
                venueId,
                mains.id,
                MenuItemDetails("Ribeye", null, Money.of("26.00"))
            )
        )
        water = items.create(CreateMenuItemCommand(venueId, mains.id, MenuItemDetails("Water", null, Money.of("2.50"))))
        doneness = modifierGroups.create(
            CreateModifierGroupCommand(
                venueId,
                "Doneness",
                1,
                1,
                listOf(ModifierDraft("Rare", Money.ZERO), ModifierDraft("Well done", Money.of("1.00"))),
            ),
        )
        items.attachModifierGroups(AttachModifierGroupsCommand(venueId, ribeye.id, listOf(doneness.id)))
    }

    private fun openOrder(): Order? = floorStatus.tableOf(venueId, table.id).order

    private val rare get() = doneness.modifiers.first { it.name == "Rare" }.id.value.toString()

    @Test
    fun `table page shows the menu with modifier groups`() {
        mockMvc.get(tablePage) { with(waiter) }.andExpect {
            status { isOk() }
            content { string(containsString("Ribeye")) }
            content { string(containsString("Doneness")) }
            content { string(containsString("data-option-id=\"$rare\"")) }
            content { string(containsString("Open table")) }
        }
    }

    @Test
    fun `stop listed dish is shown but cannot be tapped`() {
        items.changeAvailability(venueId, water.id, false)

        mockMvc.get(tablePage) { with(waiter) }.andExpect {
            content { string(containsString("Stop list")) }
        }
    }

    @Test
    fun `waiter opens the table with guests and the first round`() {
        mockMvc.post(rounds) {
            with(waiter)
            with(csrf())
            param("guests", "3")
            param("lines[0].menuItemId", ribeye.id.value.toString())
            param("lines[0].quantity", "2")
            param("lines[0].modifierIds", rare)
            param("lines[0].comment", "No salt")
            param("lines[1].menuItemId", water.id.value.toString())
            param("lines[1].quantity", "1")
            param("lines[1].comment", "")
        }.andExpect {
            redirectedUrl(tablePage)
            flash { attribute("message", "Table opened") }
            flash { attribute("sent", true) }
        }

        val order = openOrder()!!
        assertEquals(3, order.guests)
        assertEquals(1, order.rounds)
        assertEquals(Money.of("54.50"), order.total)
        assertEquals("No salt", order.lines.first { it.name == "Ribeye" }.comment)
    }

    @Test
    fun `next round is added to the open order`() {
        val order = orders.open(
            OpenOrderCommand(venueId, table.id, UserId(waiterId), lines = listOf(OrderLineRequest(water.id, 1))),
        )

        mockMvc.post(rounds) {
            with(waiter)
            with(csrf())
            param("orderId", order.id.value.toString())
            param("lines[0].menuItemId", water.id.value.toString())
            param("lines[0].quantity", "2")
        }.andExpect {
            redirectedUrl(tablePage)
            flash { attribute("message", "Round 2 sent") }
        }

        assertEquals(2, openOrder()!!.rounds)
    }

    @Test
    fun `stop listed dish keeps the basket and names the dish`() {
        items.changeAvailability(venueId, water.id, false)

        mockMvc.post(rounds) {
            with(waiter)
            with(csrf())
            param("lines[0].menuItemId", water.id.value.toString())
            param("lines[0].quantity", "1")
        }.andExpect {
            redirectedUrl(tablePage)
            flash { attribute("error", containsString("Water")) }
            flash { attribute("unavailable", water.id.value.toString()) }
        }

        assertNull(openOrder())
    }

    @Test
    fun `missing required option is rejected`() {
        mockMvc.post(rounds) {
            with(waiter)
            with(csrf())
            param("lines[0].menuItemId", ribeye.id.value.toString())
            param("lines[0].quantity", "1")
        }.andExpect {
            flash { attribute("error", containsString("Doneness")) }
        }

        assertNull(openOrder())
    }

    @Test
    fun `empty round for an open order is rejected`() {
        val order = orders.open(OpenOrderCommand(venueId, table.id, UserId(waiterId)))

        mockMvc.post(rounds) {
            with(waiter)
            with(csrf())
            param("orderId", order.id.value.toString())
        }.andExpect {
            flash { attribute("error", "A round needs at least one line") }
        }
    }

    @Test
    fun `kitchen cannot send rounds`() {
        val cookId = jdbc.createMember(venue, StaffRole.KITCHEN)

        mockMvc.post(rounds) {
            with(user(userDetailsService.loadUserByUsername("$cookId@test.passly")))
            with(csrf())
            param("lines[0].menuItemId", water.id.value.toString())
        }.andExpect { status { isForbidden() } }
    }
}
