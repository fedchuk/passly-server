package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.voucher.VoucherService
import com.passly.domain.common.Money
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class VoucherAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var vouchers: VoucherService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var manager: RequestPostProcessor

    private val venueId get() = VenueId(venue.venueId)
    private val page get() = "/admin/venues/${venue.venueId}/vouchers"
    private val nextMonth get() = vouchers.today(venueId).plusMonths(1).toString()

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        manager = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, StaffRole.MANAGER)}@test.passly"))
    }

    private fun stored(): List<Voucher> = vouchers.list(venueId)

    private fun create(
        kind: String = "SINGLE_USE",
        valueType: String = "AMOUNT",
        value: String = "25",
        validUntil: String = nextMonth,
        quantity: String = "1",
    ) = mockMvc.post(page) {
        with(manager)
        with(csrf())
        param("name", "Gift card")
        param("kind", kind)
        param("valueType", valueType)
        param("value", value)
        param("validUntil", validUntil)
        param("quantity", quantity)
    }

    @Test
    fun `page offers the form while there are no vouchers`() {
        mockMvc.get(page) { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("No vouchers yet.")) }
            content { string(containsString("<option value=\"AMOUNT\">EUR</option>")) }
            content { string(containsString("href=\"/admin/venues/${venue.venueId}/vouchers\"")) }
        }
    }

    @Test
    fun `single-use vouchers are created in a batch and listed with their codes`() {
        create(quantity = "3").andExpect {
            redirectedUrl(page)
            flash { attribute("message", "3 vouchers created") }
        }

        val created = stored()
        assertEquals(3, created.size)
        assertTrue(created.all { it.kind == VoucherKind.SINGLE_USE && it.value == VoucherValue.Amount(Money.of("25.00")) })
        mockMvc.get(page) { with(manager) }.andExpect {
            content { string(containsString(created.first().code.label)) }
            content { string(containsString("−25.00 EUR")) }
            content { string(containsString("Single use")) }
        }
    }

    @Test
    fun `reusable percent voucher is created alone`() {
        create(kind = "REUSABLE", valueType = "PERCENT", value = "15 %").andExpect {
            flash { attribute("message", "Voucher ${stored().single().code.label} created") }
        }

        assertEquals("15%", stored().single().value.label)
        create(kind = "REUSABLE", quantity = "2").andExpect {
            flash { attribute("error", "A reusable voucher is created one at a time") }
        }
    }

    @Test
    fun `wrong values and past dates are refused`() {
        create(value = "abc").andExpect { flash { attribute("error", "Value 'abc' is not a number") } }
        create(valueType = "PERCENT", value = "120").andExpect { flash { attribute("error", "Discount must be more than 0% and at most 100%") } }
        create(validUntil = vouchers.today(venueId).minusDays(1).toString()).andExpect {
            flash { attribute("error", "Valid until must be today or later") }
        }
        create(validUntil = "soon").andExpect { flash { attribute("error", "Choose the last day the voucher works") } }

        assertEquals(emptyList(), stored())
    }

    @Test
    fun `expired voucher is shown as expired and can be deleted`() {
        create()
        val voucher = stored().single()
        jdbc.sql("update voucher set valid_until = current_date - 400 where id = ?").param(voucher.id.value).update()

        mockMvc.get(page) { with(manager) }.andExpect { content { string(containsString(">Expired<")) } }

        mockMvc.post("$page/${voucher.id}/delete") {
            with(manager)
            with(csrf())
        }.andExpect { flash { attribute("message", "Voucher ${voucher.code.label} deleted") } }

        assertEquals(emptyList(), stored())
    }

    @Test
    fun `waiter cannot manage vouchers`() {
        val waiter = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, StaffRole.WAITER)}@test.passly"))

        mockMvc.get(page) { with(waiter) }.andExpect { status { isForbidden() } }
    }
}
