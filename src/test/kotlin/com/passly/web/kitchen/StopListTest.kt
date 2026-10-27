package com.passly.web.kitchen

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.domain.common.Money
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
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
import org.springframework.web.context.WebApplicationContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class StopListTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var categories: CategoryService

    @Autowired
    lateinit var items: MenuItemService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var sole: MenuItem
    lateinit var steak: MenuItem

    private val venueId get() = VenueId(venue.venueId)
    private val page get() = "/kitchen/venues/${venue.venueId}/stop-list"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        val mains = categories.create(
            CreateCategoryCommand(
                venueId,
                "Plats",
                stationId = StationId(jdbc.createStation(venue))
            )
        )
        val fish = categories.create(CreateCategoryCommand(venueId, "Poissons", parentId = mains.id))
        sole = items.create(
            CreateMenuItemCommand(
                venueId,
                fish.id,
                MenuItemDetails("Sole meunière", null, Money.of("32.00"))
            )
        )
        steak = items.create(CreateMenuItemCommand(venueId, mains.id, MenuItemDetails("Entrecôte", null, Money.of("28.00"))))
        items.changeAvailability(venueId, sole.id, false)
    }

    private fun member(role: StaffRole) = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, role)}@test.passly"))

    private fun isAvailable(item: MenuItem) = items.listInCategory(venueId, item.categoryId!!).single { it.id == item.id }.available

    @Test
    fun `kitchen sees what is off the menu with its category`() {
        mockMvc.get(page) { with(member(StaffRole.KITCHEN)) }.andExpect {
            status { isOk() }
            content { string(containsString("1 dish guests cannot order now")) }
            content { string(containsString("Sole meunière")) }
            content { string(containsString("Plats › Poissons")) }
            content { string(containsString("Back on menu")) }
        }
    }

    @Test
    fun `kitchen puts a dish on the stop list and back`() {
        val cook = member(StaffRole.KITCHEN)

        mockMvc.post("$page/${steak.id.value}") {
            with(cook)
            with(csrf())
            param("available", "false")
        }.andExpect {
            redirectedUrl(page)
            flash { attribute("message", "'Entrecôte' is on the stop list") }
        }
        assertFalse(isAvailable(steak))

        mockMvc.post("$page/${sole.id.value}") {
            with(cook)
            with(csrf())
            param("available", "true")
        }.andExpect { flash { attribute("message", "'Sole meunière' is back on the menu") } }
        assertTrue(isAvailable(sole))
    }

    @Test
    fun `waiter cannot change the stop list`() {
        val waiter = member(StaffRole.WAITER)

        mockMvc.get(page) { with(waiter) }.andExpect { status { isForbidden() } }
        mockMvc.post("$page/${steak.id.value}") {
            with(waiter)
            with(csrf())
            param("available", "false")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `manager reaches the stop list from the overview`() {
        mockMvc.get("/admin/venues/${venue.venueId}/overview") { with(member(StaffRole.MANAGER)) }.andExpect {
            content { string(containsString(page)) }
        }
    }
}
