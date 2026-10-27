package com.passly.application.order

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.application.tax.TaxRateService
import com.passly.domain.common.Money
import com.passly.domain.floor.DiningTableId
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.user.Role
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
import org.springframework.test.web.servlet.post
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OrderPaymentApiTest {

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
    lateinit var taxRates: TaxRateService

    @Autowired
    lateinit var orders: OrderService

    lateinit var venue: TestVenue
    lateinit var waiterUuid: UUID
    lateinit var waiter: MockMvc
    lateinit var tableUuid: UUID
    lateinit var order: Order

    private val venueId get() = VenueId(venue.venueId)
    private fun alcohol() = taxRates.list(venueId).single { it.name == "Alcohol" }.id
    private val payments get() = "/api/v1/venues/${venue.venueId}/orders/${order.id}/payments"

    @BeforeEach
    fun setUp() {
        venue = jdbc.createVenue()
        waiterUuid = jdbc.createMember(venue, Role.WAITER)
        waiter = context.mockMvcAs(waiterUuid)
        val hall = floors.createArea(CreateAreaCommand(venueId, "Hall"))
        tableUuid = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4)).id.value
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
                venueId, DiningTableId(tableUuid), UserId(waiterUuid),
                lines = listOf(OrderLineRequest(steak.id, 1), OrderLineRequest(wine.id, 2)),
            ),
        )
    }

    private fun pay(key: String?, method: String, amount: String, client: MockMvc = waiter) = client.post(payments) {
        contentType = MediaType.APPLICATION_JSON
        if (key != null) header("Idempotency-Key", key)
        content = """{"method": "$method", "amount": "$amount"}"""
    }

    private fun storedPayments() = orders.get(venueId, order.id).payments

    @Test
    fun `payment returns the order with what is left and the vat`() {
        pay(UUID.randomUUID().toString(), "CASH", "20.00").andExpect {
            status { isOk() }
            header { string("Idempotent-Replayed", "false") }
            jsonPath("$.status") { value("OPEN") }
            jsonPath("$.paid") { value("20.00") }
            jsonPath("$.due") { value("21.00") }
            jsonPath("$.payments[0].method") { value("CASH") }
            jsonPath("$.taxes[0].rate") { value("10.00") }
            jsonPath("$.taxes[0].tax") { value("2.55") }
            jsonPath("$.taxes[1].rate") { value("20.00") }
            jsonPath("$.taxes[1].tax") { value("2.17") }
        }
    }

    @Test
    fun `a retry with the same key replays the response and takes no money twice`() {
        val key = UUID.randomUUID().toString()
        val first = pay(key, "CARD", "20.00").andExpect { status { isOk() } }.andReturn().response.contentAsString

        val second = pay(key, "CARD", "20.00").andExpect {
            status { isOk() }
            header { string("Idempotent-Replayed", "true") }
        }.andReturn().response.contentAsString

        assertEquals(first, second)
        assertEquals(1, storedPayments().size)
    }

    @Test
    fun `the same key for a different payment is rejected`() {
        val key = UUID.randomUUID().toString()
        pay(key, "CARD", "20.00").andExpect { status { isOk() } }

        pay(key, "CARD", "21.00").andExpect {
            status { isEqualTo(422) }
            jsonPath("$.detail") { exists() }
        }
        assertEquals(1, storedPayments().size)
    }

    @Test
    fun `a payment without a key is rejected`() {
        pay(null, "CARD", "20.00").andExpect { status { isBadRequest() } }

        assertEquals(0, storedPayments().size)
    }

    @Test
    fun `a failed payment does not burn the key`() {
        val key = UUID.randomUUID().toString()

        pay(key, "CARD", "41.01").andExpect { status { isBadRequest() } }

        pay(key, "CARD", "41.00").andExpect {
            status { isOk() }
            header { string("Idempotent-Replayed", "false") }
            jsonPath("$.status") { value("CLOSED") }
            jsonPath("$.due") { value("0.00") }
            jsonPath("$.closedBy") { value(waiterUuid.toString()) }
        }
    }

    @Test
    fun `an empty order is closed without payment`() {
        val other = floors.createTable(CreateTableCommand(venueId, floors.get(venueId).areas.first().id, "8", 2))
        val empty = orders.open(OpenOrderCommand(venueId, other.id, UserId(waiterUuid)))

        waiter.post("/api/v1/venues/${venue.venueId}/orders/${empty.id}/close").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CLOSED") }
        }
        waiter.post("/api/v1/venues/${venue.venueId}/orders/${order.id}/close").andExpect { status { isConflict() } }
    }

    @Test
    fun `kitchen cannot take payments`() {
        val cook = context.mockMvcAs(jdbc.createMember(venue, Role.KITCHEN))

        pay(UUID.randomUUID().toString(), "CASH", "1.00", cook).andExpect { status { isForbidden() } }
    }
}
