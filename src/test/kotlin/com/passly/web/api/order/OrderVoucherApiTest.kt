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
import com.passly.application.order.PayOrderCommand
import com.passly.application.voucher.CreateVouchersCommand
import com.passly.application.voucher.VoucherService
import com.passly.domain.common.Money
import com.passly.domain.discount.DiscountPercent
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.order.Order
import com.passly.domain.order.OrderLineRequest
import com.passly.domain.order.PaymentMethod
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.domain.voucher.Voucher
import com.passly.domain.voucher.VoucherKind
import com.passly.domain.voucher.VoucherValue
import com.passly.web.api.*
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.put
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class OrderVoucherApiTest {

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
    lateinit var vouchers: VoucherService

    lateinit var venue: TestVenue
    lateinit var waiterUuid: UUID
    lateinit var waiter: MockMvc
    lateinit var newsletter: Voucher
    lateinit var order: Order

    private val venueId get() = VenueId(venue.venueId)
    private val voucherUrl get() = "/api/v1/venues/${venue.venueId}/orders/${order.id}/voucher"

    @BeforeEach
    fun setUp() {
        venue = jdbc.createVenue()
        waiterUuid = jdbc.createMember(venue, StaffRole.WAITER)
        waiter = context.mockMvcAs(waiterUuid)
        val hall = floors.createArea(CreateAreaCommand(venueId, "Hall"))
        val table = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4))
        val mains = categories.create(CreateCategoryCommand(venueId, "Mains", stationId = StationId(jdbc.createStation(venue))))
        val steak = items.create(CreateMenuItemCommand(venueId, mains.id, MenuItemDetails("Steak", null, Money.of("28.00"))))
        newsletter = vouchers.create(
            CreateVouchersCommand(
                venueId, "Newsletter", VoucherKind.REUSABLE, VoucherValue.Percent(DiscountPercent.of("15")), vouchers.today(venueId),
            ),
        ).single()
        order = orders.open(OpenOrderCommand(venueId, table.id, UserId(waiterUuid), lines = listOf(OrderLineRequest(steak.id, 1))))
    }

    private fun apply(code: String, client: MockMvc = waiter) = client.put(voucherUrl) {
        contentType = MediaType.APPLICATION_JSON
        content = """{"code": "$code"}"""
    }

    @Test
    fun `voucher is applied by its code on its last day`() {
        apply(newsletter.code.value.lowercase()).andExpect {
            status { isOk() }
            jsonPath("$.total") { value("28.00") }
            jsonPath("$.voucher.voucherId") { value(newsletter.id.toString()) }
            jsonPath("$.voucher.code") { value(newsletter.code.label) }
            jsonPath("$.voucher.name") { value("Newsletter") }
            jsonPath("$.voucher.kind") { value("REUSABLE") }
            jsonPath("$.voucher.percent") { value("15.00") }
            jsonPath("$.voucher.value") { doesNotExist() }
            jsonPath("$.voucher.amount") { value("4.20") }
            jsonPath("$.voucher.appliedBy") { value(waiterUuid.toString()) }
            jsonPath("$.toPay") { value("23.80") }
            jsonPath("$.due") { value("23.80") }
        }
    }

    @Test
    fun `voucher is removed`() {
        apply(newsletter.code.label)

        waiter.delete(voucherUrl).andExpect {
            status { isOk() }
            jsonPath("$.voucher") { doesNotExist() }
            jsonPath("$.toPay") { value("28.00") }
        }
    }

    @Test
    fun `malformed, unknown and expired codes are refused`() {
        apply("ABC").andExpect { status { isBadRequest() } }
        apply("QQQQ-2222").andExpect { status { isNotFound() } }

        jdbc.sql("update voucher set valid_until = current_date - 400 where id = ?").param(newsletter.id.value).update()

        apply(newsletter.code.label).andExpect { status { isConflict() } }
    }

    @Test
    fun `voucher cannot change after the first payment`() {
        apply(newsletter.code.label)
        orders.pay(PayOrderCommand(venueId, order.id, UserId(waiterUuid), PaymentMethod.CASH, Money.of("10.00")))

        apply(newsletter.code.label).andExpect { status { isConflict() } }
        waiter.delete(voucherUrl).andExpect { status { isConflict() } }
    }

    @Test
    fun `kitchen cannot apply vouchers`() {
        apply(newsletter.code.label, context.mockMvcAs(jdbc.createMember(venue, StaffRole.KITCHEN))).andExpect {
            status { isForbidden() }
        }
    }
}
