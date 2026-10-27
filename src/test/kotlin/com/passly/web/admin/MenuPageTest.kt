package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.domain.common.Money
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
                MenuItemDetails(name, null, Money.of(price))
            )
        )

    private fun open(url: String) =
        mockMvc.get(url) { with(manager) }.andExpect { status { isOk() } }.andReturn().modelAndView!!.model

    @Suppress("UNCHECKED_CAST")
    private fun roots(url: String) = open(url)["roots"] as List<MenuNodeView>

    @Test
    fun `all categories are closed when the menu opens`() {
        val roots = roots(page)

        assertEquals(listOf("Entrées", "Plats"), roots.map { it.name })
        assertEquals(listOf(false, false), roots.map { it.open })
        assertEquals(listOf(0, 2), roots.map { it.subcategories })
        assertEquals(listOf(1, 0), roots.map { it.items })
        assertTrue(roots.all { it.children.isEmpty() && it.dishes.isEmpty() })
    }

    @Test
    fun `top-level category opens with its dishes`() {
        val entrees = roots("$page/categories/${starters.id}/items")[0]

        assertTrue(entrees.open)
        assertEquals(listOf("Soupe à l'oignon"), entrees.dishes.map { it.name })
    }

    @Test
    fun `open category lists its subcategories one under another`() {
        val plats = roots("$page/categories/${mains.id}/items")[1]

        assertTrue(plats.open)
        assertEquals(listOf("Viandes" to 2, "Poissons" to 0), plats.children.map { it.name to it.items })
        assertTrue(plats.children.none { it.open })
        assertTrue(plats.dishes.isEmpty())
    }

    @Test
    fun `subcategory opens inside its parent with its dishes`() {
        val roots = roots("$page/categories/${meat.id}/items")

        assertEquals(listOf(false, true), roots.map { it.open })
        val viandes = roots[1].children.single { it.open }
        assertEquals("Viandes", viandes.name)
        assertEquals(mains.id.value, viandes.parentId)
        assertEquals(listOf("Entrecôte", "Magret de canard"), viandes.dishes.map { it.name })
        assertEquals(listOf(false, true), viandes.dishes.map { it.available })
        assertEquals("28.00", viandes.dishes.first().price)
        assertTrue(roots[1].children.single { it.name == "Poissons" }.dishes.isEmpty())
    }

    @Test
    fun `deeper subcategory opens inside the open branch`() {
        val grill = categories.create(CreateCategoryCommand(venueId, "Grillades", parentId = meat.id))

        val viandes = roots("$page/categories/${grill.id}/items")[1].children.single { it.open }

        assertEquals(listOf("Grillades" to true), viandes.children.map { it.name to it.open })
    }

    @Test
    fun `nested lists can be reordered by their own handle`() {
        mockMvc.get("$page/categories/${meat.id}/items") { with(manager) }.andExpect {
            content { string(containsString("data-parent-id=\"${mains.id.value}\"")) }
            content { string(containsString("data-handle=\".handle-d1\"")) }
        }
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

        assertEquals(emptyList<MenuNodeView>(), model["roots"])
    }
}
