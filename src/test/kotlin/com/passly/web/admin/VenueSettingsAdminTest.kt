package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.floor.CreateAreaCommand
import com.passly.application.floor.CreateTableCommand
import com.passly.application.floor.FloorPlanService
import com.passly.application.order.OpenOrderCommand
import com.passly.application.order.OrderService
import com.passly.application.venue.AcceptCurrencyCommand
import com.passly.application.venue.VenueSettingsService
import com.passly.domain.user.UserId
import com.passly.domain.venue.CurrencyCode
import com.passly.domain.venue.ExchangeRate
import com.passly.domain.venue.VenueId
import com.passly.web.api.StaffRole
import com.passly.web.api.TestVenue
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
import com.passly.web.api.securedMockMvc
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class VenueSettingsAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var settings: VenueSettingsService

    @Autowired
    lateinit var floors: FloorPlanService

    @Autowired
    lateinit var orders: OrderService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue

    private val venueId get() = VenueId(venue.venueId)
    private val page get() = "/admin/venues/${venue.venueId}/settings"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
    }

    private fun member(role: StaffRole): RequestPostProcessor =
        user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, role)}@test.passly"))

    private fun save(country: String, currency: String, version: Long? = settings.get(venueId).version) =
        mockMvc.post(page) {
            with(member(StaffRole.MANAGER))
            with(csrf())
            param("country", country)
            param("currency", currency)
            version?.let { param("version", it.toString()) }
        }

    private fun acceptSwissFrancs() =
        settings.acceptCurrency(AcceptCurrencyCommand(venueId, CurrencyCode.of("CHF"), ExchangeRate.of("1.05")))

    @Test
    fun `new venue works in France with euros`() {
        val attributes = mockMvc.get(page) { with(member(StaffRole.OWNER)) }.andExpect {
            status { isOk() }
            view { name("admin/settings") }
            model { attribute("currencyLocked", false) }
            content { string(containsString("EUR · Euro")) }
        }.andReturn().modelAndView!!.model

        val form = attributes["form"] as VenueSettingsForm
        assertEquals("FR", form.country)
        assertEquals("EUR", form.currency)
    }

    @Test
    fun `manager moves the venue to Switzerland`() {
        save("CH", "CHF").andExpect {
            redirectedUrl(page)
            flash { attribute("message", "Settings saved") }
        }

        val stored = settings.get(venueId)
        assertEquals("CH", stored.country.value)
        assertEquals("CHF", stored.currency.value)
    }

    @Test
    fun `main currency is fixed after the first order`() {
        val hall = floors.createArea(CreateAreaCommand(venueId, "Hall"))
        val table = floors.createTable(CreateTableCommand(venueId, hall.id, "7", 4))
        orders.open(OpenOrderCommand(venueId, table.id, UserId(jdbc.createMember(venue, StaffRole.WAITER))))

        mockMvc.get(page) { with(member(StaffRole.MANAGER)) }.andExpect {
            model { attribute("currencyLocked", true) }
            content { string(containsString("the main currency is fixed")) }
        }

        save("CH", "CHF").andExpect {
            status { isOk() }
            model { attributeHasErrors("form") }
            content { string(containsString("cannot change after the first order")) }
        }
        assertEquals("EUR", settings.get(venueId).currency.value)
    }

    @Test
    fun `currency without cents is shown as a field error`() {
        save("JP", "JPY").andExpect {
            status { isOk() }
            model { attributeHasFieldErrors("form", "currency") }
        }
        assertEquals("FR", settings.get(venueId).country.value)
    }

    @Test
    fun `stale form shows a conflict`() {
        save("CH", "CHF", version = 99).andExpect {
            status { isOk() }
            content { string(containsString("changed by someone else")) }
        }
    }

    @Test
    fun `waiter cannot open the settings`() {
        mockMvc.get(page) { with(member(StaffRole.WAITER)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `settings link is in the sidebar for the manager`() {
        mockMvc.get("/admin/venues/${venue.venueId}/overview") { with(member(StaffRole.MANAGER)) }.andExpect {
            content { string(containsString("/admin/venues/${venue.venueId}/settings")) }
        }
    }

    @Test
    fun `manager accepts swiss francs at a rate`() {
        mockMvc.post("$page/currencies") {
            with(member(StaffRole.MANAGER))
            with(csrf())
            param("currency", "CHF")
            param("rate", "1.05")
        }.andExpect {
            redirectedUrl(page)
            flash { attribute("message", "CHF is accepted at 1.05") }
        }

        assertEquals(ExchangeRate.of("1.05"), settings.get(venueId).accepted(CurrencyCode.of("CHF")).rate)
        mockMvc.get(page) { with(member(StaffRole.MANAGER)) }.andExpect {
            content { string(containsString("1 CHF =")) }
            content { string(containsString("value=\"1.05\"")) }
        }
    }

    @Test
    fun `rate is updated`() {
        acceptSwissFrancs()

        mockMvc.post("$page/currencies/CHF") {
            with(member(StaffRole.MANAGER))
            with(csrf())
            param("rate", "1.07")
        }.andExpect {
            redirectedUrl(page)
            flash { attribute("message", "Rate of CHF is now 1.07") }
        }

        assertEquals(ExchangeRate.of("1.07"), settings.get(venueId).accepted(CurrencyCode.of("CHF")).rate)
    }

    @Test
    fun `currency is no longer accepted`() {
        acceptSwissFrancs()

        mockMvc.post("$page/currencies/CHF/delete") {
            with(member(StaffRole.MANAGER))
            with(csrf())
        }.andExpect {
            redirectedUrl(page)
            flash { attribute("message", "CHF is no longer accepted") }
        }

        assertTrue(settings.get(venueId).acceptedCurrencies.isEmpty())
    }

    @Test
    fun `rate that is not a number is shown as an error`() {
        mockMvc.post("$page/currencies") {
            with(member(StaffRole.MANAGER))
            with(csrf())
            param("currency", "CHF")
            param("rate", "one")
        }.andExpect {
            redirectedUrl(page)
            flash { attribute("error", "Exchange rate 'one' is not a number") }
        }

        assertTrue(settings.get(venueId).acceptedCurrencies.isEmpty())
    }

    @Test
    fun `main and accepted currencies are not offered again`() {
        acceptSwissFrancs()

        val attributes = mockMvc.get(page) { with(member(StaffRole.MANAGER)) }.andReturn().modelAndView!!.model

        @Suppress("UNCHECKED_CAST")
        val addable = (attributes["addableCurrencies"] as List<CodeOption>).map { it.code }
        assertFalse("EUR" in addable)
        assertFalse("CHF" in addable)
        assertTrue("USD" in addable)
    }

    @Test
    fun `main currency cannot change while other currencies are set against it`() {
        acceptSwissFrancs()

        save("FR", "USD").andExpect {
            status { isOk() }
            content { string(containsString("Remove the additional currencies first")) }
        }
        assertEquals("EUR", settings.get(venueId).currency.value)
    }

    @Test
    fun `waiter cannot add a currency`() {
        mockMvc.post("$page/currencies") {
            with(member(StaffRole.WAITER))
            with(csrf())
            param("currency", "CHF")
            param("rate", "1.05")
        }.andExpect { status { isForbidden() } }
    }
}
