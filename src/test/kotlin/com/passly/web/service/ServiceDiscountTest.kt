package com.passly.web.service

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
import com.passly.domain.floor.DiningTable
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.OrderStatus
import com.passly.domain.order.PaymentMethod
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
class ServiceDiscountTest {

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
    lateinit var discounts: DiscountService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var waiterId: UUID
    lateinit var waiter: RequestPostProcessor
    lateinit var table: DiningTable
    lateinit var order: Order
    lateinit var regular: VenueDiscount
    lateinit var onTheHouse: VenueDiscount

    private val venueId get() = VenueId(venue.venueId)
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
        val mains = categories.create(CreateCategoryCommand(venueId, "Plats", stationId = StationId(jdbc.createStation(venue))))
        val steak = items.create(CreateMenuItemCommand(venueId, mains.id, MenuItemDetails("Entrecôte", null, Money.of("28.00"))))
        regular = discounts.create(CreateDiscountCommand(venueId, "Regular guest", DiscountPercent.of("10")))
        onTheHouse = discounts.create(CreateDiscountCommand(venueId, "On the house", DiscountPercent.of("100")))
        order = orders.open(OpenOrderCommand(venueId, table.id, UserId(waiterId), lines = listOf(OrderLineRequest(steak.id, 1))))
    }

    private fun apply(discount: VenueDiscount, by: RequestPostProcessor = waiter) = mockMvc.post("$tablePage/discount") {
        with(by)
        with(csrf())
        param("orderId", order.id.value.toString())
        param("discountId", discount.id.value.toString())
    }

    private fun stored(): Order = orders.get(venueId, order.id)

    @Test
    fun `bill offers the discounts of the venue`() {
        mockMvc.get(tablePage) { with(waiter) }.andExpect {
            status { isOk() }
            content { string(containsString("Regular guest · 10%")) }
            content { string(containsString("Apply discount")) }
        }
    }

    @Test
    fun `applied discount lowers what is due and the vat`() {
        apply(regular).andExpect {
            redirectedUrl(tablePage)
            flash { attribute("message", "Regular guest applied: −2.80, 25.20 to pay") }
        }

        assertEquals(Money.of("25.20"), stored().due)
        mockMvc.get(tablePage) { with(waiter) }.andExpect {
            content { string(containsString("−2.80")) }
            content { string(containsString("25.20")) }
            content { string(containsString("2.29")) }
            content { string(containsString("Remove discount")) }
        }
        mockMvc.get(floor) { with(waiter) }.andExpect { content { string(containsString("25.20")) } }
    }

    @Test
    fun `discount is removed`() {
        apply(regular)

        mockMvc.post("$tablePage/discount/remove") {
            with(waiter)
            with(csrf())
            param("orderId", order.id.value.toString())
        }.andExpect { flash { attribute("message", "Discount removed, 28.00 to pay") } }

        assertNull(stored().discount)
    }

    @Test
    fun `discount is locked after the first payment`() {
        orders.pay(PayOrderCommand(venueId, order.id, UserId(waiterId), PaymentMethod.CASH, Money.of("10.00")))

        apply(regular).andExpect { flash { attribute("error", "The discount cannot change after the first payment") } }
        mockMvc.get(tablePage) { with(waiter) }.andExpect { content { string(doesNotContain("Apply discount")) } }
    }

    @Test
    fun `a bill on the house is closed with the close button`() {
        apply(onTheHouse)

        mockMvc.post("$tablePage/close") {
            with(waiter)
            with(csrf())
            param("orderId", order.id.value.toString())
        }.andExpect { redirectedUrl(floor) }

        assertEquals(OrderStatus.CLOSED, stored().status)
    }

    @Test
    fun `kitchen cannot apply discounts`() {
        val cook = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, StaffRole.KITCHEN)}@test.passly"))

        apply(regular, cook).andExpect { status { isForbidden() } }
    }
}
