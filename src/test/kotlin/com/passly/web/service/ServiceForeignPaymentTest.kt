package com.passly.web.service

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
import com.passly.application.venue.AcceptCurrencyCommand
import com.passly.application.venue.VenueSettingsService
import com.passly.domain.common.Money
import com.passly.domain.floor.DiningTable
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.user.Role
import com.passly.domain.user.UserId
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.ExchangeRate
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
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ServiceForeignPaymentTest {

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
    lateinit var settings: VenueSettingsService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
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
        val waiterId = jdbc.createMember(venue, Role.WAITER)
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
            ),
        )
        order = orders.open(
            OpenOrderCommand(
                venueId,
                table.id,
                UserId(waiterId),
                lines = listOf(OrderLineRequest(steak.id, 1))
            )
        )
    }

    private fun pay(method: String, amount: String, currency: String? = null) = mockMvc.post("$tablePage/payments") {
        with(waiter)
        with(csrf())
        param("orderId", order.id.value.toString())
        param("method", method)
        param("amount", amount)
        currency?.let { param("currency", it) }
    }

    private fun stored(): Order = orders.get(venueId, order.id)

    private fun acceptSwissFrancs() =
        settings.acceptCurrency(AcceptCurrencyCommand(venueId, CurrencyCode.of("CHF"), ExchangeRate.of("1.05")))

    @Test
    fun `cash above the due closes the table and reports the change`() {
        pay("CASH", "50.00").andExpect {
            redirectedUrl(floor)
            flash { attribute("message", "Table closed, 28.00 paid, change 22.00") }
        }

        assertEquals(Money.of("22.00"), stored().payments.single().change)
    }

    @Test
    fun `payment form offers the accepted currencies with their rates`() {
        acceptSwissFrancs()

        mockMvc.get(tablePage) { with(waiter) }.andExpect {
            content { string(containsString("data-main=\"EUR\"")) }
            content { string(containsString("data-rate=\"1.05\"")) }
        }
    }

    @Test
    fun `swiss francs are converted at the venue rate`() {
        acceptSwissFrancs()

        pay("CASH", "20.00", "CHF").andExpect {
            redirectedUrl(tablePage)
            flash { attribute("message", "Cash 20.00 CHF (21.00) taken, 7.00 left to pay") }
        }

        val payment = stored().payments.single()
        assertEquals(Money.of("21.00"), payment.amount)
        assertEquals("CHF", payment.tender.currency.value)
        mockMvc.get(tablePage) { with(waiter) }.andExpect {
            content { string(containsString("20.00 CHF at 1.05")) }
        }
    }

    @Test
    fun `currency the venue does not accept is refused`() {
        pay("CASH", "10.00", "USD").andExpect {
            redirectedUrl(tablePage)
            flash { attribute("error", "USD is not accepted here") }
        }

        assertEquals(emptyList(), stored().payments)
    }
}
