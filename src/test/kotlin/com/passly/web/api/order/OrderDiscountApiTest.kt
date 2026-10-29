package com.passly.web.api.order

import com.passly.TestcontainersConfiguration
import com.passly.application.discount.CreateDiscountCommand
import com.passly.application.discount.DiscountService
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.application.order.OpenOrderCommand
import com.passly.application.order.OrderService
import com.passly.application.order.PayOrderCommand
import com.passly.domain.common.Money
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.discount.VenueDiscount
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.PaymentMethod
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OrderDiscountApiTest {

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
    lateinit var discounts: DiscountService

    lateinit var venue: TestVenue
    lateinit var waiterUuid: UUID
    lateinit var waiter: MockMvc
    lateinit var regular: VenueDiscount
    lateinit var order: Order

    private val venueId get() = VenueId(venue.venueId)
    private val discountUrl get() = "/api/v1/venues/${venue.venueId}/orders/${order.id}/discount"

    @BeforeEach
    fun setUp() {
        venue = jdbc.createVenue()
        waiterUuid = jdbc.createMember(venue, StaffRole.WAITER)
        waiter = context.mockMvcAs(waiterUuid)
        val hall = floors.createArea(CreateAreaCommand(venueId, "Hall"))
        val table = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4))
        val mains = categories.create(CreateCategoryCommand(venueId, "Mains", stationId = StationId(jdbc.createStation(venue))))
        val steak = items.create(CreateMenuItemCommand(venueId, mains.id, MenuItemDetails("Steak", null, Money.of("28.00"))))
        regular = discounts.create(CreateDiscountCommand(venueId, "Regular guest", DiscountPercent.of("10")))
        order = orders.open(OpenOrderCommand(venueId, table.id, UserId(waiterUuid), lines = listOf(OrderLineRequest(steak.id, 1))))
    }

    private fun apply(client: MockMvc = waiter, discountId: UUID = regular.id.value) = client.put(discountUrl) {
        contentType = MediaType.APPLICATION_JSON
        content = """{"discountId": "$discountId"}"""
    }

    @Test
    fun `staff see the discounts of the venue`() {
        waiter.get("/api/v1/venues/${venue.venueId}/discounts").andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value(regular.id.toString()) }
            jsonPath("$[0].name") { value("Regular guest") }
            jsonPath("$[0].percent") { value("10.00") }
        }
    }

    @Test
    fun `waiter applies a discount and the bill shows what is left to pay`() {
        apply().andExpect {
            status { isOk() }
            jsonPath("$.total") { value("28.00") }
            jsonPath("$.discount.discountId") { value(regular.id.toString()) }
            jsonPath("$.discount.name") { value("Regular guest") }
            jsonPath("$.discount.percent") { value("10.00") }
            jsonPath("$.discount.amount") { value("2.80") }
            jsonPath("$.discount.appliedBy") { value(waiterUuid.toString()) }
            jsonPath("$.toPay") { value("25.20") }
            jsonPath("$.due") { value("25.20") }
            jsonPath("$.taxes[0].gross") { value("25.20") }
        }

        waiter.get("/api/v1/venues/${venue.venueId}/orders/${order.id}").andExpect {
            jsonPath("$.discount.name") { value("Regular guest") }
        }
    }

    @Test
    fun `discount is removed`() {
        apply()

        waiter.delete(discountUrl).andExpect {
            status { isOk() }
            jsonPath("$.discount") { doesNotExist() }
            jsonPath("$.toPay") { value("28.00") }
        }
    }

    @Test
    fun `discount cannot change after the first payment`() {
        orders.pay(PayOrderCommand(venueId, order.id, UserId(waiterUuid), PaymentMethod.CASH, Money.of("10.00")))

        apply().andExpect { status { isConflict() } }
        waiter.delete(discountUrl).andExpect { status { isConflict() } }
    }

    @Test
    fun `unknown discount is not found`() {
        apply(discountId = UUID.randomUUID()).andExpect { status { isNotFound() } }
    }

    @Test
    fun `kitchen cannot apply discounts`() {
        val cook = context.mockMvcAs(jdbc.createMember(venue, StaffRole.KITCHEN))

        apply(cook).andExpect { status { isForbidden() } }
    }
}
