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
import com.passly.application.order.OrderService
import com.passly.application.venue.AcceptCurrencyCommand
import com.passly.application.venue.UpdateVenueSettingsCommand
import com.passly.application.venue.VenueSettingsService
import com.passly.domain.common.Money
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.user.Role
import com.passly.domain.user.UserId
import com.passly.domain.venue.CountryCode
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.ExchangeRate
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
class ForeignPaymentApiTest {

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

    @Autowired
    lateinit var settings: VenueSettingsService

    lateinit var venue: TestVenue
    lateinit var waiter: MockMvc
    lateinit var order: Order

    private val venueId get() = VenueId(venue.venueId)
    private val payments get() = "/api/v1/venues/${venue.venueId}/orders/${order.id}/payments"

    @BeforeEach
    fun setUp() {
        venue = jdbc.createVenue()
        settings.update(UpdateVenueSettingsCommand(venueId, CountryCode.of("CH"), CurrencyCode.of("CHF")))
        settings.acceptCurrency(AcceptCurrencyCommand(venueId, CurrencyCode.of("EUR"), ExchangeRate.of("0.95")))
        val waiterUuid = jdbc.createMember(venue, Role.WAITER)
        waiter = context.mockMvcAs(waiterUuid)
        val hall = floors.createArea(CreateAreaCommand(venueId, "Hall"))
        val table = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4))
        val mains = categories.create(
            CreateCategoryCommand(
                venueId,
                "Plats",
                stationId = StationId(jdbc.createStation(venue))
            )
        )
        val rosti = items.create(
            CreateMenuItemCommand(
                venueId,
                mains.id,
                MenuItemDetails("Rösti", null, Money.of("22.50"))
            ),
        )
        order = orders.open(
            OpenOrderCommand(venueId, table.id, UserId(waiterUuid), lines = listOf(OrderLineRequest(rosti.id, 2))),
        )
    }

    private fun pay(body: String) = waiter.post(payments) {
        contentType = MediaType.APPLICATION_JSON
        header("Idempotency-Key", UUID.randomUUID().toString())
        content = body
    }

    @Test
    fun `app learns the main and accepted currencies`() {
        waiter.get("/api/v1/venues/${venue.venueId}/currencies").andExpect {
            status { isOk() }
            jsonPath("$.main") { value("CHF") }
            jsonPath("$.accepted[0].currency") { value("EUR") }
            jsonPath("$.accepted[0].rate") { value("0.95") }
            jsonPath("$.accepted[0].rateSetAt") { exists() }
        }
    }

    @Test
    fun `euro cash pays the bill and the change comes back in francs`() {
        pay("""{"method": "CASH", "amount": "50.00", "currency": "EUR"}""").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CLOSED") }
            jsonPath("$.paid") { value("45.00") }
            jsonPath("$.payments[0].amount") { value("45.00") }
            jsonPath("$.payments[0].tendered") { value("50.00") }
            jsonPath("$.payments[0].currency") { value("EUR") }
            jsonPath("$.payments[0].rate") { value("0.95") }
            jsonPath("$.payments[0].change") { value("2.50") }
        }
    }

    @Test
    fun `payment without a currency is in the main one`() {
        pay("""{"method": "CARD", "amount": "10.00"}""").andExpect {
            status { isOk() }
            jsonPath("$.payments[0].currency") { value("CHF") }
            jsonPath("$.payments[0].rate") { doesNotExist() }
            jsonPath("$.due") { value("35.00") }
        }
    }

    @Test
    fun `card above the due in euros is refused`() {
        pay("""{"method": "CARD", "amount": "50.00", "currency": "EUR"}""").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `currency the venue does not accept is refused`() {
        pay("""{"method": "CASH", "amount": "10.00", "currency": "USD"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("USD is not accepted here") }
        }
    }
}
