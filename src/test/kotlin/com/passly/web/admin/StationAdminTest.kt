package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateStationCommand
import com.passly.application.menu.StationService
import com.passly.domain.user.Role
import com.passly.domain.venue.VenueId
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

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class StationAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var stations: StationService

    @Autowired
    lateinit var categories: CategoryService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var manager: RequestPostProcessor

    private val venueId get() = VenueId(venue.venueId)
    private val base get() = "/admin/venues/${venue.venueId}/stations"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        manager = member(Role.MANAGER)
    }

    private fun member(role: Role): RequestPostProcessor =
        user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, role)}@test.passly"))

    private fun names() = stations.list(venueId).map { it.name }

    @Test
    fun `page lists the stations and the sidebar links to it`() {
        stations.create(CreateStationCommand(venueId, "Kitchen"))

        mockMvc.get(base) { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("Kitchen")) }
            content { string(containsString("href=\"$base\"")) }
            content { string(containsString("Modifier groups")) }
        }
    }

    @Test
    fun `station is added, renamed and moved`() {
        stations.create(CreateStationCommand(venueId, "Kitchen"))

        mockMvc.post(base) {
            with(manager)
            with(csrf())
            param("name", "Bar")
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Station 'Bar' added") }
        }
        val bar = stations.list(venueId).single { it.name == "Bar" }

        mockMvc.post("$base/${bar.id}/rename") {
            with(manager)
            with(csrf())
            param("name", "Cocktail bar")
            param("version", "0")
        }.andExpect { flash { attribute("message", "Station renamed to 'Cocktail bar'") } }

        mockMvc.post("$base/${bar.id}/shift") {
            with(manager)
            with(csrf())
            param("delta", "-1")
        }.andExpect { redirectedUrl(base) }

        assertEquals(listOf("Cocktail bar", "Kitchen"), names())
    }

    @Test
    fun `duplicate name and station in use are reported`() {
        val kitchen = stations.create(CreateStationCommand(venueId, "Kitchen"))
        categories.create(CreateCategoryCommand(venueId, "Food", stationId = kitchen.id))

        mockMvc.post(base) {
            with(manager)
            with(csrf())
            param("name", "kitchen")
        }.andExpect { flash { attribute("error", "Station 'kitchen' already exists") } }

        mockMvc.post("$base/${kitchen.id}/delete") {
            with(manager)
            with(csrf())
        }.andExpect {
            redirectedUrl(base)
            flash { attributeExists("error") }
        }

        assertEquals(listOf("Kitchen"), names())
    }

    @Test
    fun `waiter cannot open the stations page`() {
        mockMvc.get(base) { with(member(Role.WAITER)) }.andExpect { status { isForbidden() } }
    }
}