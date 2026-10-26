package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.Category
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.web.context.WebApplicationContext
import kotlin.test.*

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MenuPageTest {

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
    lateinit var manager: RequestPostProcessor
    lateinit var starters: Category
    lateinit var mains: Category
    lateinit var meat: Category
    lateinit var steak: MenuItem

    private val venueId get() = VenueId(venue.venueId)
    private val page get() = "/admin/venues/${venue.venueId}"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        val kitchen = StationId(jdbc.createStation(venue))
        starters = categories.create(CreateCategoryCommand(venueId, "Entrées", stationId = kitchen))
        mains = categories.create(CreateCategoryCommand(venueId, "Plats", stationId = kitchen))
        meat = categories.create(CreateCategoryCommand(venueId, "Viandes", parentId = mains.id))
        categories.create(CreateCategoryCommand(venueId, "Poissons", parentId = mains.id))
        dish(starters, "Soupe à l'oignon", "9.50")
        steak = dish(meat, "Entrecôte", "28.00")
        dish(meat, "Magret de canard", "24.00")
        items.changeAvailability(venueId, steak.id, false)
        manager = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, Role.MANAGER)}@test.passly"))
    }

    private fun dish(category: Category, name: String, price: String) =
        items.create(
            CreateMenuItemCommand(
                venueId,
                category.id,
                MenuItemDetails(name, null, Money.of(price), TaxRate.of("10"))
            )
        )

    private fun open(url: String) =
        mockMvc.get(url) { with(manager) }.andExpect { status { isOk() } }.andReturn().modelAndView!!.model

    @Test
    fun `first top-level category is open by default`() {
        val model = open(page)

        @Suppress("UNCHECKED_CAST")
        val roots = model["roots"] as List<MenuRootView>
        assertEquals(listOf("Entrées", "Plats"), roots.map { it.name })
        assertEquals(listOf(true, false), roots.map { it.open })
        assertEquals(listOf(0, 2), roots.map { it.subcategories })

        val focus = model["focus"] as MenuFocusView
        assertEquals("Entrées", focus.name)
        assertTrue(focus.root)
        assertEquals(listOf("Soupe à l'oignon"), focus.items.map { it.name })
    }

    @Test
    fun `subcategory stays in the row of its siblings and shows its dishes`() {
        val model = open("$page/categories/${meat.id}/items")

        @Suppress("UNCHECKED_CAST")
        val roots = model["roots"] as List<MenuRootView>
        assertEquals(listOf(false, true), roots.map { it.open })

        val focus = model["focus"] as MenuFocusView
        assertFalse(focus.root)
        val row = focus.levels.single()
        assertEquals(mains.id.value, row.parentId)
        assertEquals(listOf("Viandes" to true, "Poissons" to false), row.cards.map { it.name to it.selected })
        assertEquals(listOf(mains.id.value, row.cards[1].id), row.cards.map { it.targetId })
        assertEquals(listOf("Entrecôte", "Magret de canard"), focus.items.map { it.name })
        assertEquals(listOf(false, true), focus.items.map { it.available })
        assertEquals("28.00", focus.items.first().price)
    }

    @Test
    fun `root shows its subcategories as cards with dish counts`() {
        val focus = open("$page/categories/${mains.id}/items")["focus"] as MenuFocusView

        val row = focus.levels.single()
        assertEquals(listOf("Viandes" to 2, "Poissons" to 0), row.cards.map { it.name to it.items })
        assertTrue(row.cards.none { it.selected })
        assertTrue(focus.items.isEmpty())
    }

    @Test
    fun `deeper subcategory adds a second row under the first`() {
        val grill = categories.create(CreateCategoryCommand(venueId, "Grillades", parentId = meat.id))

        val focus = open("$page/categories/${grill.id}/items")["focus"] as MenuFocusView

        assertEquals(listOf(mains.id.value, meat.id.value), focus.levels.map { it.parentId })
        assertEquals(listOf("Viandes", "Grillades"), focus.levels.map { level -> level.cards.single { it.selected }.name })
    }

    @Test
    fun `top-level category without subcategories has no rows`() {
        val focus = open(page)["focus"] as MenuFocusView

        assertTrue(focus.levels.isEmpty())
    }

    @Test
    fun `page lists dishes with price and stop list state`() {
        mockMvc.get("$page/categories/${meat.id}/items") { with(manager) }.andExpect {
            content { string(containsString("Entrecôte")) }
            content { string(containsString("28.00")) }
            content { string(containsString("Stop list")) }
            content { string(containsString("In stock")) }
            content { string(containsString("+ Add subcategory")) }
        }
    }

    @Test
    fun `page loads the drag and drop scripts`() {
        mockMvc.get(page) { with(manager) }.andExpect {
            content { string(containsString("/webjars/sortablejs/1.15.7/Sortable.min.js")) }
            content { string(containsString("/js/menu-order.js")) }
            content { string(containsString("data-order-url=\"/admin/venues/${venue.venueId}/categories/order\"")) }
        }
    }

    @Test
    fun `drag and drop scripts are served without a login`() {
        mockMvc.get("/webjars/sortablejs/1.15.7/Sortable.min.js").andExpect { status { isOk() } }
        mockMvc.get("/js/menu-order.js").andExpect { status { isOk() } }
    }

    @Test
    fun `empty menu has nothing to open`() {
        val empty = jdbc.createVenue()
        val owner = user(userDetailsService.loadUserByUsername("${jdbc.createMember(empty, Role.MANAGER)}@test.passly"))

        val model = mockMvc.get("/admin/venues/${empty.venueId}") { with(owner) }
            .andExpect { status { isOk() } }.andReturn().modelAndView!!.model

        assertNull(model["focus"])
        assertEquals(emptyList<MenuRootView>(), model["roots"])
    }
}
