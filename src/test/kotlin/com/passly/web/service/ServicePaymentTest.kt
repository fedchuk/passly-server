package com.passly.web.service

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.application.order.FloorStatusQuery
import com.passly.application.order.OpenOrderCommand
import com.passly.application.order.OrderService
import com.passly.application.tax.TaxRateService
import com.passly.domain.common.Money
import com.passly.domain.floor.DiningTable
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.OrderStatus
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
class ServicePaymentTest {

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
    lateinit var taxRates: TaxRateService

    @Autowired
    lateinit var orders: OrderService

    @Autowired
    lateinit var floorStatus: FloorStatusQuery

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var waiterId: UUID
    lateinit var waiter: RequestPostProcessor
    lateinit var table: DiningTable
    lateinit var order: Order

    private val venueId get() = VenueId(venue.venueId)
    private fun alcohol() = taxRates.list(venueId).single { it.name == "Alcohol" }.id
    private val floor get() = "/service/venues/${venue.venueId}"
    private val tablePage get() = "$floor/tables/${table.id.value}"

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
                "Plats",
                stationId = StationId(jdbc.createStation(venue))
            )
        )
        val steak = items.create(
            CreateMenuItemCommand(
                venueId,
                mains.id,
                MenuItemDetails("Entrecôte", null, Money.of("28.00"))
            )
        )
        val wine = items.create(CreateMenuItemCommand(venueId, mains.id, MenuItemDetails("Bordeaux, verre", null, Money.of("6.50"), taxRateId = alcohol())))
        order = orders.open(
            OpenOrderCommand(
                venueId,
                table.id,
                UserId(waiterId),
                lines = listOf(OrderLineRequest(steak.id, 1), OrderLineRequest(wine.id, 2))
            ),
        )
    }

    private fun pay(method: String, amount: String) = mockMvc.post("$tablePage/payments") {
        with(waiter)
        with(csrf())
        param("orderId", order.id.value.toString())
        param("method", method)
        param("amount", amount)
    }

    private fun stored(): Order = orders.get(venueId, order.id)

    @Test
    fun `bill shows the total, vat per rate and the payment form`() {
        mockMvc.get(tablePage) { with(waiter) }.andExpect {
            status { isOk() }
            content { string(containsString("To pay")) }
            content { string(containsString("41.00")) }
            content { string(containsString("incl. VAT 10%")) }
            content { string(containsString("2.55")) }
            content { string(containsString("incl. VAT 20%")) }
            content { string(containsString("2.17")) }
            content { string(containsString("value=\"CASH\"")) }
        }
    }

    @Test
    fun `part payment keeps the table open and shows what is left`() {
        pay("CASH", "20,00").andExpect {
            redirectedUrl(tablePage)
            flash { attribute("message", "Cash 20.00 taken, 21.00 left to pay") }
        }

        assertEquals(OrderStatus.OPEN, stored().status)
        assertEquals(Money.of("21.00"), stored().due)
    }

    @Test
    fun `full payment closes the table and returns to the floor`() {
        pay("CASH", "20.00")

        pay("CARD", "21.00").andExpect {
            redirectedUrl(floor)
            flash { attribute("message", "Table closed, 41.00 paid") }
        }

        assertEquals(OrderStatus.CLOSED, stored().status)
        assertNull(floorStatus.tableOf(venueId, table.id).order)
    }

    @Test
    fun `wrong amounts are rejected without a payment`() {
        pay("CARD", "abc").andExpect { flash { attribute("error", "Enter an amount like 12.50") } }
        pay("CARD", "41.01").andExpect { flash { attribute("error", "Amount 41.01 is more than the 41.00 due") } }

        assertEquals(emptyList(), stored().payments)
    }

    @Test
    fun `an empty order is closed with the close button`() {
        val hall = floors.get(venueId).areas.first()
        val other = floors.createTable(CreateTableCommand(venueId, hall.id, "8", 2))
        val empty = orders.open(OpenOrderCommand(venueId, other.id, UserId(waiterId)))

        mockMvc.post("$floor/tables/${other.id.value}/close") {
            with(waiter)
            with(csrf())
            param("orderId", empty.id.value.toString())
        }.andExpect {
            redirectedUrl(floor)
            flash { attribute("message", "Table closed") }
        }

        assertEquals(OrderStatus.CLOSED, orders.get(venueId, empty.id).status)
    }

    @Test
    fun `a table with something to pay cannot be closed with the button`() {
        mockMvc.post("$tablePage/close") {
            with(waiter)
            with(csrf())
            param("orderId", order.id.value.toString())
        }.andExpect {
            redirectedUrl(tablePage)
            flash { attribute("error", "Table still has 41.00 to pay") }
        }
    }

    @Test
    fun `kitchen cannot take payments`() {
        val cookId = jdbc.createMember(venue, StaffRole.KITCHEN)

        mockMvc.post("$tablePage/payments") {
            with(user(userDetailsService.loadUserByUsername("$cookId@test.passly")))
            with(csrf())
            param("orderId", order.id.value.toString())
            param("method", "CASH")
            param("amount", "1.00")
        }.andExpect { status { isForbidden() } }
    }
}
