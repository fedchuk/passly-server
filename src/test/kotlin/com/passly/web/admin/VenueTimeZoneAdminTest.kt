package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.venue.VenueSettingsService
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
class VenueTimeZoneAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var settings: VenueSettingsService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var manager: RequestPostProcessor

    private val venueId get() = VenueId(venue.venueId)
    private val page get() = "/admin/venues/${venue.venueId}/settings"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        manager = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, StaffRole.MANAGER)}@test.passly"))
    }

    private fun save(timeZone: String) = mockMvc.post(page) {
        with(manager)
        with(csrf())
        param("country", "ES")
        param("currency", "EUR")
        param("timeZone", timeZone)
        param("version", settings.get(venueId).version.toString())
    }

    @Test
    fun `new venue counts its day in Paris`() {
        mockMvc.get(page) { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("selected=\"selected\">Europe/Paris</option>")) }
            content { string(containsString("America/Argentina/Buenos Aires")) }
        }
    }

    @Test
    fun `manager moves the venue to the Canary Islands`() {
        save("Atlantic/Canary").andExpect { flash { attribute("message", "Settings saved") } }

        assertEquals("Atlantic/Canary", settings.get(venueId).timeZone.id)
    }

    @Test
    fun `unknown time zone is refused`() {
        save("Europe/Atlantis").andExpect {
            status { isOk() }
            content { string(containsString("Unknown time zone Europe/Atlantis")) }
        }

        assertEquals("Europe/Paris", settings.get(venueId).timeZone.id)
    }
}
