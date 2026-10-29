package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.discount.DiscountService
import com.passly.domain.discount.VenueDiscount
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
class DiscountAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var discounts: DiscountService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var manager: RequestPostProcessor

    private val venueId get() = VenueId(venue.venueId)
    private val settingsPage get() = "/admin/venues/${venue.venueId}/settings"
    private val base get() = "$settingsPage/discounts"
    private val back get() = "$settingsPage#discounts"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        manager = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, StaffRole.MANAGER)}@test.passly"))
    }

    private fun stored(): List<VenueDiscount> = discounts.list(venueId)

    private fun add(name: String, percent: String) = mockMvc.post(base) {
        with(manager)
        with(csrf())
        param("name", name)
        param("percent", percent)
    }

    @Test
    fun `settings show an empty list of discounts`() {
        mockMvc.get(settingsPage) { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("No discounts yet.")) }
            content { string(containsString("Add discount")) }
        }
    }

    @Test
    fun `discount is added with a comma or a percent sign`() {
        add("Happy hour", "12,5 %").andExpect {
            redirectedUrl(back)
            flash { attribute("message", "Discount 'Happy hour' added") }
        }

        assertEquals(listOf("Happy hour · 12.5%"), stored().map { it.label })
        mockMvc.get(settingsPage) { with(manager) }.andExpect {
            content { string(containsString("value=\"Happy hour\"")) }
            content { string(containsString("value=\"12.5\"")) }
        }
    }

    @Test
    fun `wrong percent and duplicate name are refused`() {
        add("Happy hour", "20")

        add("Free", "abc").andExpect { flash { attribute("error", "Discount 'abc' is not a number") } }
        add("Too much", "150").andExpect { flash { attribute("error", "Discount must be more than 0% and at most 100%") } }
        add("happy HOUR", "10").andExpect { flash { attribute("error", "Discount 'happy HOUR' already exists") } }

        assertEquals(1, stored().size)
    }

    @Test
    fun `discount is changed and a stale form is refused`() {
        add("Happy hour", "20")
        val happyHour = stored().single()

        mockMvc.post("$base/${happyHour.id}") {
            with(manager)
            with(csrf())
            param("name", "Late happy hour")
            param("percent", "25")
            param("version", happyHour.version.toString())
        }.andExpect { flash { attribute("message", "Discount 'Late happy hour' saved") } }

        mockMvc.post("$base/${happyHour.id}") {
            with(manager)
            with(csrf())
            param("name", "Happy hour")
            param("percent", "20")
            param("version", happyHour.version.toString())
        }.andExpect { flash { attribute("error", "Discount 'Late happy hour' was changed by someone else. Reload and try again.") } }

        assertEquals("Late happy hour · 25%", stored().single().label)
    }

    @Test
    fun `discount is deleted`() {
        add("Happy hour", "20")

        mockMvc.post("$base/${stored().single().id}/delete") {
            with(manager)
            with(csrf())
        }.andExpect { flash { attribute("message", "Discount 'Happy hour' deleted") } }

        assertEquals(emptyList(), stored())
    }

    @Test
    fun `waiter cannot manage discounts`() {
        val waiter = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, StaffRole.WAITER)}@test.passly"))

        mockMvc.post(base) {
            with(waiter)
            with(csrf())
            param("name", "Free beer")
            param("percent", "100")
        }.andExpect { status { isForbidden() } }
    }
}
