package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.application.venue.VenueOverview
import com.passly.application.venue.VenueOverviewQuery
import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.user.Role
import com.passly.domain.venue.VenueId
import com.passly.web.api.*
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class VenueOverviewAdminTest {

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

    @Autowired
    lateinit var overviews: VenueOverviewQuery

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var managerId: UUID

    private val venueId get() = VenueId(venue.venueId)
    private val station by lazy { StationId(jdbc.createStation(venue)) }

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        managerId = jdbc.createMember(venue, Role.MANAGER)
    }

    private fun loggedIn(userId: UUID) = user(userDetailsService.loadUserByUsername("$userId@test.passly"))

    private fun addItem(categoryName: String, vararg names: String) {
        val category = categories.create(CreateCategoryCommand(venueId, categoryName, stationId = station))
        names.forEach {
            items.create(
                CreateMenuItemCommand(
                    venueId,
                    category.id,
                    MenuItemDetails(it, null, Money.of("5.00"), TaxRate.of("10"))
                )
            )
        }
    }

    @Test
    fun `overview counts the menu and the team of one venue only`() {
        addItem("Wine", "Rioja", "Albariño")
        addItem("Beer", "Estrella")
        val rioja = items.listInCategory(venueId, categories.getTree(venueId).categories.single { it.name == "Wine" }.id).first()
        items.changeAvailability(venueId, rioja.id, false)
        jdbc.createMember(venue, Role.WAITER)

        val other = jdbc.createVenue()
        jdbc.createMember(other, Role.WAITER)

        assertEquals(
            VenueOverview(categories = 2, items = 3, stopListed = 1, modifierGroups = 0, staff = 2),
            overviews.overviewOf(venueId),
        )
    }

    @Test
    fun `manager sees the overview without the staff section`() {
        addItem("Wine", "Rioja")

        mockMvc.get("/admin/venues/${venue.venueId}/overview") { with(loggedIn(managerId)) }.andExpect {
            status { isOk() }
            content { string(containsString("Menu items")) }
            content { string(not(containsString("/admin/venues/${venue.venueId}/staff"))) }
        }
    }

    @Test
    fun `owner sees the link to the staff`() {
        val ownerId = jdbc.createMember(venue, Role.OWNER)

        mockMvc.get("/admin/venues/${venue.venueId}/overview") { with(loggedIn(ownerId)) }.andExpect {
            status { isOk() }
            content { string(containsString("/admin/venues/${venue.venueId}/staff")) }
        }
    }

    @Test
    fun `waiter cannot open the overview`() {
        val waiterId = jdbc.createMember(venue, Role.WAITER)

        mockMvc.get("/admin/venues/${venue.venueId}/overview") { with(loggedIn(waiterId)) }.andExpect {
            status { isForbidden() }
        }
    }
}
