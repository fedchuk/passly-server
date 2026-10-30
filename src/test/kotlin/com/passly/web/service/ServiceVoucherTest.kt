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
import com.passly.application.order.ApplyDiscountCommand
import com.passly.application.order.OpenOrderCommand
import com.passly.application.order.OrderService
import com.passly.application.voucher.CreateVouchersCommand
import com.passly.application.voucher.VoucherService
import com.passly.domain.common.Money
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.floor.DiningTable
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.OrderStatus
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.domain.voucher.Voucher
import com.passly.domain.voucher.VoucherKind
import com.passly.domain.voucher.VoucherValue
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
class ServiceVoucherTest {

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

    @Autowired
    lateinit var vouchers: VoucherService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var waiterId: UUID
    lateinit var waiter: RequestPostProcessor
    lateinit var table: DiningTable
    lateinit var order: Order
    lateinit var gift: Voucher

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
        gift = singleUse("10.00")
        order = orders.open(OpenOrderCommand(venueId, table.id, UserId(waiterId), lines = listOf(OrderLineRequest(steak.id, 1))))
    }

    private fun singleUse(amount: String): Voucher = vouchers.create(
        CreateVouchersCommand(
            venueId, "Gift card", VoucherKind.SINGLE_USE, VoucherValue.Amount(Money.of(amount)), vouchers.today(venueId).plusDays(30),
        ),
    ).single()

    private fun apply(code: String, by: RequestPostProcessor = waiter) = mockMvc.post("$tablePage/voucher") {
        with(by)
        with(csrf())
        param("orderId", order.id.value.toString())
        param("code", code)
    }

    private fun stored(): Order = orders.get(venueId, order.id)

    @Test
    fun `waiter enters a code in lower case and the bill shows the voucher`() {
        apply(gift.code.value.lowercase()).andExpect {
            redirectedUrl(tablePage)
            flash { attribute("message", "Voucher ${gift.code.label} applied: −10.00, 18.00 to pay") }
        }

        assertEquals(gift.id, stored().voucher?.voucherId)
        mockMvc.get(tablePage) { with(waiter) }.andExpect {
            content { string(containsString("Voucher ${gift.code.label} · Gift card")) }
            content { string(containsString("−10.00")) }
            content { string(containsString("Remove voucher")) }
        }
    }

    @Test
    fun `voucher counts after the discount`() {
        val regular = discounts.create(CreateDiscountCommand(venueId, "Regular guest", DiscountPercent.of("50")))
        orders.applyDiscount(ApplyDiscountCommand(venueId, order.id, regular.id, UserId(waiterId)))

        apply(gift.code.label)

        assertEquals(Money.of("4.00"), stored().due)
    }

    @Test
    fun `wrong, unknown and used codes are explained`() {
        apply("12").andExpect { flash { attribute("error", "Enter a voucher code like ABCD-2345") } }
        apply("QQQQ-2222").andExpect { flash { attribute("error", "Voucher QQQQ-2222 not found") } }

        apply(gift.code.label)
        val hall = floors.get(venueId).areas.first()
        val other = floors.createTable(CreateTableCommand(venueId, hall.id, "8", 2))
        order = orders.open(OpenOrderCommand(venueId, other.id, UserId(waiterId)))

        apply(gift.code.label).andExpect { flash { attribute("error", "Voucher ${gift.code.label} was already used") } }
    }

    @Test
    fun `removed voucher is free for another bill`() {
        apply(gift.code.label)

        mockMvc.post("$tablePage/voucher/remove") {
            with(waiter)
            with(csrf())
            param("orderId", order.id.value.toString())
        }.andExpect { flash { attribute("message", "Voucher removed, 28.00 to pay") } }

        assertNull(stored().voucher)
        assertNull(vouchers.list(venueId).single().redeemedOrderId)
    }

    @Test
    fun `expired voucher no longer works`() {
        jdbc.sql("update voucher set valid_until = current_date - 400 where id = ?").param(gift.id.value).update()

        apply(gift.code.label).andExpect { flash { attribute("error", containsString("Voucher ${gift.code.label} expired on")) } }
        assertNull(stored().voucher)
    }

    @Test
    fun `a voucher covering the whole bill leaves the table to close`() {
        val big = singleUse("50.00")
        apply(big.code.label)

        mockMvc.post("$tablePage/close") {
            with(waiter)
            with(csrf())
            param("orderId", order.id.value.toString())
        }.andExpect { redirectedUrl(floor) }

        assertEquals(OrderStatus.CLOSED, stored().status)
        assertEquals(order.id, vouchers.list(venueId).single { it.id == big.id }.redeemedOrderId)
    }

    @Test
    fun `kitchen cannot apply vouchers`() {
        val cook = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, StaffRole.KITCHEN)}@test.passly"))

        apply(gift.code.label, cook).andExpect { status { isForbidden() } }
    }
}
