package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.tax.CreateTaxRateCommand
import com.passly.application.tax.TaxRateService
import com.passly.domain.common.TaxRate
import com.passly.domain.tax.VenueTaxRate
import com.passly.domain.user.Role
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
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class TaxRateAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var taxRates: TaxRateService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var manager: RequestPostProcessor

    private val venueId get() = VenueId(venue.venueId)
    private val settingsPage get() = "/admin/venues/${venue.venueId}/settings"
    private val base get() = "$settingsPage/tax-rates"
    private val back get() = "$settingsPage#tax-rates"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        manager = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, Role.MANAGER)}@test.passly"))
    }

    private fun stored(): List<VenueTaxRate> = taxRates.list(venueId)

    private fun add(name: String, rate: String) = mockMvc.post(base) {
        with(manager)
        with(csrf())
        param("name", name)
        param("rate", rate)
    }

    @Test
    fun `settings offer the usual french rates while the list is empty`() {
        mockMvc.get(settingsPage) { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("No tax rates yet.")) }
            content { string(containsString("Add the usual rates for France")) }
            content { string(containsString("Food and soft drinks 10%, Alcohol 20%, Reduced 5.5%")) }
        }
    }

    @Test
    fun `usual rates are added in one click`() {
        mockMvc.post("$base/presets") {
            with(manager)
            with(csrf())
        }.andExpect {
            redirectedUrl(back)
            flash { attribute("message", "Added Food and soft drinks, Alcohol, Reduced") }
        }

        assertEquals(listOf("10%", "20%", "5.5%"), stored().map { it.percentLabel })
        mockMvc.get(settingsPage) { with(manager) }.andExpect {
            content { string(containsString("value=\"Alcohol\"")) }
        }
    }

    @Test
    fun `manager adds a rate with a comma`() {
        add("Takeaway", "5,5").andExpect {
            redirectedUrl(back)
            flash { attribute("message", "Tax rate 'Takeaway' added") }
        }

        assertEquals(listOf("Takeaway" to "5.5%"), stored().map { it.name to it.percentLabel })
    }

    @Test
    fun `rate that is not a number is refused`() {
        add("Alcohol", "twenty").andExpect {
            redirectedUrl(back)
            flash { attribute("error", "Tax rate 'twenty' is not a number") }
        }

        assertTrue(stored().isEmpty())
    }

    @Test
    fun `second rate with the same name is refused`() {
        taxRates.create(CreateTaxRateCommand(venueId, "Alcohol", TaxRate.of("20")))

        add("ALCOHOL", "21").andExpect { flash { attribute("error", "Tax rate 'ALCOHOL' already exists") } }

        assertEquals(1, stored().size)
    }

    @Test
    fun `rate is renamed and changed`() {
        val alcohol = taxRates.create(CreateTaxRateCommand(venueId, "Alcohol", TaxRate.of("20")))

        mockMvc.post("$base/${alcohol.id}") {
            with(manager)
            with(csrf())
            param("name", "Wine and spirits")
            param("rate", "21")
            param("version", stored().single().version.toString())
        }.andExpect {
            redirectedUrl(back)
            flash { attribute("message", "Tax rate 'Wine and spirits' saved") }
        }

        assertEquals("Wine and spirits" to "21%", stored().single().let { it.name to it.percentLabel })
    }

    @Test
    fun `rate is deleted`() {
        val alcohol = taxRates.create(CreateTaxRateCommand(venueId, "Alcohol", TaxRate.of("20")))

        mockMvc.post("$base/${alcohol.id}/delete") {
            with(manager)
            with(csrf())
        }.andExpect { flash { attribute("message", "Tax rate 'Alcohol' deleted") } }

        assertTrue(stored().isEmpty())
    }

    @Test
    fun `waiter cannot change tax rates but the app can read them`() {
        val waiterId = jdbc.createMember(venue, Role.WAITER)
        taxRates.create(CreateTaxRateCommand(venueId, "Alcohol", TaxRate.of("20")))

        mockMvc.post(base) {
            with(user(userDetailsService.loadUserByUsername("$waiterId@test.passly")))
            with(csrf())
            param("name", "Food")
            param("rate", "10")
        }.andExpect { status { isForbidden() } }

        context.mockMvcAs(waiterId).get("/api/v1/venues/${venue.venueId}/tax-rates").andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("Alcohol") }
            jsonPath("$[0].rate") { value("20.00") }
        }
    }
}
