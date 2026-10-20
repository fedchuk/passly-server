package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.domain.menu.CategoryId
import com.passly.domain.user.Role
import com.passly.domain.venue.VenueId
import com.passly.web.api.TestVenue
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
import com.passly.web.api.securedMockMvc
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
import java.util.UUID
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

    private val venueId get() = VenueId(venue.venueId)
    private val page get() = "/admin/venues/${venue.venueId}"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        val managerId = jdbc.createMember(venue, Role.MANAGER)
        manager = user(userDetailsService.loadUserByUsername("$managerId@test.passly"))
    }

    private fun create(name: String, parentId: CategoryId? = null) =
        categories.create(CreateCategoryCommand(venueId, name, parentId))

    private fun names(parentId: CategoryId? = null) =
        categories.getTree(venueId).children(parentId).map { it.name }

    @Test
    fun `new category is added and a message is shown`() {
        mockMvc.post("$page/categories") {
            with(manager)
            with(csrf())
            param("name", "Wine")
        }.andExpect {
            redirectedUrl(page)
            flash { attribute("message", "Category 'Wine' added") }
        }

        assertEquals(listOf("Wine"), names())
    }

    @Test
    fun `subcategory goes under its parent`() {
        val wine = create("Wine")

        mockMvc.post("$page/categories") {
            with(manager)
            with(csrf())
            param("name", "Rioja")
            param("parentId", wine.id.toString())
        }.andExpect { redirectedUrl(page) }

        assertEquals(listOf("Rioja"), names(wine.id))
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
            param("version", "0")
            param("_active", "on")
        }.andExpect {
            redirectedUrl(page)
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
    fun `category moves up`() {
        create("Wine")
        val beer = create("Beer")

        mockMvc.post("$page/categories/${beer.id}/shift") {
            with(manager)
            with(csrf())
            param("delta", "-1")
        }.andExpect { redirectedUrl(page) }

        assertEquals(listOf("Beer", "Wine"), names())
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
}
