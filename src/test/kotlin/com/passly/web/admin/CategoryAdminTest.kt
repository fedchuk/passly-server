package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.StationId
import com.passly.domain.venue.VenueId
import com.passly.web.api.*
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFalse

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CategoryAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var categories: CategoryService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var manager: RequestPostProcessor
    lateinit var kitchenUuid: UUID

    private val venueId get() = VenueId(venue.venueId)
    private val page get() = "/admin/venues/${venue.venueId}"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        kitchenUuid = jdbc.createStation(venue)
        val managerId = jdbc.createMember(venue, StaffRole.MANAGER)
        manager = user(userDetailsService.loadUserByUsername("$managerId@test.passly"))
    }

    private fun create(name: String, parentId: CategoryId? = null, station: StationId? = null) =
        categories.create(
            CreateCategoryCommand(
                venueId,
                name,
                parentId,
                station ?: StationId(kitchenUuid).takeIf { parentId == null })
        )

    private fun names(parentId: CategoryId? = null) =
        categories.getTree(venueId).children(parentId).map { it.name }

    @Test
    fun `new category is added and a message is shown`() {
        val response = mockMvc.post("$page/categories") {
            with(manager)
            with(csrf())
            param("name", "Wine")
            param("stationId", kitchenUuid.toString())
        }.andExpect {
            flash { attribute("message", "Category 'Wine' added") }
        }.andReturn().response

        val wine = categories.getTree(venueId).children(null).single()
        assertEquals("Wine", wine.name)
        assertEquals("$page/categories/${wine.id}/items", response.redirectedUrl)
    }

    @Test
    fun `subcategory goes under its parent`() {
        val wine = create("Wine")

        val response = mockMvc.post("$page/categories") {
            with(manager)
            with(csrf())
            param("name", "Rioja")
            param("parentId", wine.id.toString())
        }.andReturn().response

        val rioja = categories.getTree(venueId).children(wine.id).single()
        assertEquals("Rioja", rioja.name)
        assertEquals("$page/categories/${rioja.id}/items", response.redirectedUrl)
    }

    @Test
    fun `blank name is not saved`() {
        mockMvc.post("$page/categories") {
            with(manager)
            with(csrf())
            param("name", "   ")
        }.andExpect {
            redirectedUrl(page)
            flash { attribute("error", "Name must not be blank") }
        }

        assertTrue(names().isEmpty())
    }

    @Test
    fun `form without a csrf token is rejected`() {
        mockMvc.post("$page/categories") {
            with(manager)
            param("name", "Wine")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `edit page shows the current values`() {
        val wine = create("Wine")

        mockMvc.get("$page/categories/${wine.id}/edit") { with(manager) }.andExpect {
            status { isOk() }
            view { name("admin/category-edit") }
            content { string(containsString("value=\"Wine\"")) }
        }
    }

    @Test
    fun `category is renamed and hidden`() {
        val wine = create("Wine")

        mockMvc.post("$page/categories/${wine.id}") {
            with(manager)
            with(csrf())
            param("name", "Red wine")
            param("stationId", kitchenUuid.toString())
            param("version", "0")
            param("_active", "on")
        }.andExpect {
            redirectedUrl("$page/categories/${wine.id}/items")
            flash { attribute("message", "Category 'Red wine' saved") }
        }

        val stored = categories.getTree(venueId)[wine.id]
        assertEquals("Red wine", stored.name)
        assertFalse(stored.active)
    }

    @Test
    fun `stale version shows the form again with an error`() {
        val wine = create("Wine")

        mockMvc.post("$page/categories/${wine.id}") {
            with(manager)
            with(csrf())
            param("name", "Red wine")
            param("version", "7")
            param("active", "true")
        }.andExpect {
            status { isOk() }
            view { name("admin/category-edit") }
            model { attributeHasErrors("form") }
            content { string(containsString("changed by someone else")) }
        }

        assertEquals("Wine", categories.getTree(venueId)[wine.id].name)
    }

    @Test
    fun `blank name on edit shows a field error`() {
        val wine = create("Wine")

        mockMvc.post("$page/categories/${wine.id}") {
            with(manager)
            with(csrf())
            param("name", "")
            param("version", "0")
        }.andExpect {
            status { isOk() }
            model { attributeHasFieldErrors("form", "name") }
            content { string(containsString("Name must not be blank")) }
        }
    }

    @Test
    fun `categories are put in the dragged order`() {
        val wine = create("Wine")
        val beer = create("Beer")
        val cider = create("Cider")

        mockMvc.post("$page/categories/order") {
            with(manager)
            with(csrf())
            param("orderedIds", cider.id.toString(), wine.id.toString(), beer.id.toString())
        }.andExpect { status { isNoContent() } }

        assertEquals(listOf("Cider", "Wine", "Beer"), names())
    }

    @Test
    fun `subcategories are reordered under their parent`() {
        val drinks = create("Drinks")
        val wine = create("Wine", drinks.id)
        val beer = create("Beer", drinks.id)

        mockMvc.post("$page/categories/order") {
            with(manager)
            with(csrf())
            param("parentId", drinks.id.toString())
            param("orderedIds", beer.id.toString(), wine.id.toString())
        }.andExpect { status { isNoContent() } }

        assertEquals(listOf("Beer", "Wine"), names(drinks.id))
    }

    @Test
    fun `order of another level is refused`() {
        val drinks = create("Drinks")
        val wine = create("Wine", drinks.id)

        mockMvc.post("$page/categories/order") {
            with(manager)
            with(csrf())
            param("orderedIds", wine.id.toString(), drinks.id.toString())
        }.andExpect { status { isEqualTo(422) } }

        assertEquals(listOf("Drinks"), names())
    }

    @Test
    fun `empty category is deleted`() {
        val wine = create("Wine")

        mockMvc.post("$page/categories/${wine.id}/delete") {
            with(manager)
            with(csrf())
        }.andExpect {
            redirectedUrl(page)
            flash { attribute("message", "Category 'Wine' deleted") }
        }

        assertTrue(names().isEmpty())
    }

    @Test
    fun `category with subcategories is kept and an error is shown`() {
        val wine = create("Wine")
        create("Rioja", wine.id)

        mockMvc.post("$page/categories/${wine.id}/delete") {
            with(manager)
            with(csrf())
        }.andExpect {
            redirectedUrl(page)
            flash { attributeExists("error") }
        }

        assertEquals(listOf("Wine"), names())
    }

    @Test
    fun `unknown category shows the not found page`() {
        mockMvc.get("$page/categories/${UUID.randomUUID()}/edit") { with(manager) }.andExpect {
            status { isNotFound() }
            view { name("error/404") }
        }
    }

    @Test
    fun `move options exclude the category and its subtree`() {
        val drinks = create("Drinks")
        val wine = create("Wine", drinks.id)
        create("Rioja", wine.id)
        val beer = create("Beer")

        mockMvc.get("$page/categories/${wine.id}/edit") { with(manager) }.andExpect {
            status { isOk() }
            model {
                attribute(
                    "parentOptions",
                    listOf(
                        ParentOption(null, "Top level"),
                        ParentOption(drinks.id.value, "Drinks"),
                        ParentOption(beer.id.value, "Beer"),
                    ),
                )
                attribute("currentParentId", drinks.id.value)
            }
        }
    }

    @Test
    fun `category moves under another parent`() {
        val drinks = create("Drinks")
        val wine = create("Wine")

        mockMvc.post("$page/categories/${wine.id}/move") {
            with(manager)
            with(csrf())
            param("parentId", drinks.id.toString())
        }.andExpect {
            redirectedUrl(page)
            flash { attribute("message", "Category 'Wine' moved") }
        }

        assertEquals(listOf("Wine"), names(drinks.id))
    }

    @Test
    fun `empty choice moves the category to the top level`() {
        val drinks = create("Drinks")
        create("Wine", drinks.id, station = StationId(jdbc.createStation(venue, "Bar")))
        val wine = categories.getTree(venueId).children(drinks.id).single()

        mockMvc.post("$page/categories/${wine.id}/move") {
            with(manager)
            with(csrf())
            param("parentId", "")
        }.andExpect { redirectedUrl(page) }

        assertEquals(listOf("Drinks", "Wine"), names())
    }

    @Test
    fun `moving into its own subcategory is refused`() {
        val drinks = create("Drinks")
        val wine = create("Wine", drinks.id)

        mockMvc.post("$page/categories/${drinks.id}/move") {
            with(manager)
            with(csrf())
            param("parentId", wine.id.toString())
        }.andExpect {
            redirectedUrl(page)
            flash { attributeExists("error") }
        }

        assertEquals(listOf("Drinks"), names())
    }

    @Test
    fun `a top-level category without a station is refused`() {
        mockMvc.post("$page/categories") {
            with(manager)
            with(csrf())
            param("name", "Wine")
        }.andExpect {
            redirectedUrl(page)
            flash { attribute("error", "Choose a station for a top-level category") }
        }

        assertTrue(names().isEmpty())
    }

    @Test
    fun `menu page offers stations and shows the station of each top-level category`() {
        val wine = create("Wine")

        mockMvc.get("$page/categories/${wine.id.value}/items") { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("name=\"stationId\"")) }
            content { string(containsString("Test kitchen")) }
            content { string(containsString("Same station as the parent")) }
        }
    }

    @Test
    fun `subcategory edit page offers the station of the parent`() {
        val wine = create("Wine")
        val reds = create("Reds", wine.id)

        mockMvc.get("$page/categories/${reds.id}/edit") { with(manager) }.andExpect {
            status { isOk() }
            model { attribute("topLevel", false) }
            content { string(containsString("Same as the parent (Test kitchen)")) }
        }
    }
}
