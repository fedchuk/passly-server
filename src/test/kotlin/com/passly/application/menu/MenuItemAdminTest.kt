package com.passly.application.menu

import com.passly.TestcontainersConfiguration
import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.ModifierDraft
import com.passly.domain.menu.StationId
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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MenuItemAdminTest {

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
    lateinit var modifierGroups: ModifierGroupService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var manager: RequestPostProcessor
    lateinit var wineId: UUID

    private val wine get() = CategoryId(wineId)

    private val venueId get() = VenueId(venue.venueId)
    private val base get() = "/admin/venues/${venue.venueId}"
    private val wineItems get() = "$base/categories/$wine/items"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        val managerId = jdbc.createMember(venue, Role.MANAGER)
        manager = user(userDetailsService.loadUserByUsername("$managerId@test.passly"))
        wineId = categories.create(CreateCategoryCommand(venueId, "Wine")).id.value
    }

    private fun createItem(name: String, price: String = "4.50"): MenuItem =
        items.create(CreateMenuItemCommand(venueId, wine,
            MenuItemDetails(name, null, Money.of(price), TaxRate.of("10"))
        ))

    private fun createStation(name: String): StationId {
        val id = UUID.randomUUID()
        jdbc.sql("insert into station (id, tenant_id, venue_id, name) values (?, ?, ?, ?)")
            .params(id, venue.tenantId, venue.venueId, name)
            .update()
        return StationId(id)
    }

    private fun names() = items.listInCategory(venueId, wine).map { it.name }

    @Test
    fun `category page lists its items with prices`() {
        createItem("Rioja", "4.5")
        createItem("Albariño", "5")

        mockMvc.get(wineItems) { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("Rioja")) }
            content { string(containsString("Albariño")) }
            content { string(containsString("4.50")) }
        }
    }

    @Test
    fun `new item accepts a comma as decimal separator`() {
        mockMvc.post(wineItems) {
            with(manager)
            with(csrf())
            param("name", "Rioja")
            param("price", "4,50")
            param("taxRate", "10")
        }.andExpect {
            redirectedUrl(wineItems)
            flash { attribute("message", "'Rioja' added") }
        }

        val rioja = items.listInCategory(venueId, wine).single()
        assertEquals(Money.of("4.50"), rioja.price)
    }

    @Test
    fun `invalid price shows the form again`() {
        mockMvc.post(wineItems) {
            with(manager)
            with(csrf())
            param("name", "Rioja")
            param("price", "4.555")
            param("taxRate", "10")
        }.andExpect {
            status { isOk() }
            view { name("admin/item-form") }
            model { attributeHasFieldErrors("form", "price") }
            content { string(containsString("Enter a price like 4.50")) }
        }

        assertTrue(names().isEmpty())
    }

    @Test
    fun `item gets its own station and modifier groups`() {
        val bar = createStation("Bar")
        val size = modifierGroups.create(
            CreateModifierGroupCommand(venueId, "Glass size", 1, 1, listOf(ModifierDraft("Small", Money.of("0")))),
        )

        mockMvc.post(wineItems) {
            with(manager)
            with(csrf())
            param("name", "Rioja")
            param("price", "4.50")
            param("taxRate", "10")
            param("stationId", bar.toString())
            param("modifierGroupIds", size.id.toString())
        }.andExpect { redirectedUrl(wineItems) }

        val rioja = items.listInCategory(venueId, wine).single()
        assertEquals(bar, rioja.stationId)
        assertEquals(listOf(size.id), rioja.modifierGroupIds)
    }

    @Test
    fun `edit page shows the current values`() {
        val rioja = createItem("Rioja")

        mockMvc.get("$base/items/${rioja.id}/edit") { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("value=\"Rioja\"")) }
            content { string(containsString("value=\"4.50\"")) }
        }
    }

    @Test
    fun `item is updated and its modifier groups are cleared`() {
        val size = modifierGroups.create(
            CreateModifierGroupCommand(venueId, "Glass size", 1, 1, listOf(ModifierDraft("Small", Money.of("0")))),
        )
        val rioja = createItem("Rioja")
        mockMvc.post("$base/items/${rioja.id}") {
            with(manager)
            with(csrf())
            param("name", "Rioja")
            param("price", "4.50")
            param("taxRate", "10")
            param("version", "0")
            param("modifierGroupIds", size.id.toString())
        }

        mockMvc.post("$base/items/${rioja.id}") {
            with(manager)
            with(csrf())
            param("name", "Rioja Reserva")
            param("description", "Tempranillo, 3 years")
            param("price", "6")
            param("taxRate", "10")
            param("version", items.get(venueId, rioja.id).version.toString())
            param("_modifierGroupIds", "on")
        }.andExpect {
            redirectedUrl(wineItems)
            flash { attribute("message", "'Rioja Reserva' saved") }
        }

        val stored = items.get(venueId, rioja.id)
        assertEquals("Rioja Reserva", stored.name)
        assertEquals("Tempranillo, 3 years", stored.description)
        assertEquals(Money.of("6.00"), stored.price)
        assertTrue(stored.modifierGroupIds.isEmpty())
    }

    @Test
    fun `stale version shows the form again with an error`() {
        val rioja = createItem("Rioja")

        mockMvc.post("$base/items/${rioja.id}") {
            with(manager)
            with(csrf())
            param("name", "Rioja Reserva")
            param("price", "6")
            param("taxRate", "10")
            param("version", "9")
        }.andExpect {
            status { isOk() }
            content { string(containsString("changed by someone else")) }
        }

        assertEquals("Rioja", items.get(venueId, rioja.id).name)
    }

    @Test
    fun `item goes on the stop list and back`() {
        val rioja = createItem("Rioja")

        mockMvc.post("$base/items/${rioja.id}/availability") {
            with(manager)
            with(csrf())
            param("available", "false")
        }.andExpect {
            redirectedUrl(wineItems)
            flash { attribute("message", "'Rioja' is on the stop list") }
        }
        assertFalse(items.get(venueId, rioja.id).available)

        mockMvc.post("$base/items/${rioja.id}/availability") {
            with(manager)
            with(csrf())
            param("available", "true")
        }
        assertTrue(items.get(venueId, rioja.id).available)
    }

    @Test
    fun `item moves up`() {
        createItem("Rioja")
        val albarino = createItem("Albariño")

        mockMvc.post("$base/items/${albarino.id}/shift") {
            with(manager)
            with(csrf())
            param("delta", "-1")
        }.andExpect { redirectedUrl(wineItems) }

        assertEquals(listOf("Albariño", "Rioja"), names())
    }

    @Test
    fun `archived item leaves the category`() {
        val rioja = createItem("Rioja")

        mockMvc.post("$base/items/${rioja.id}/archive") {
            with(manager)
            with(csrf())
        }.andExpect {
            redirectedUrl(wineItems)
            flash { attribute("message", "'Rioja' archived") }
        }

        assertTrue(names().isEmpty())
    }

    @Test
    fun `unknown item shows the not found page`() {
        mockMvc.get("$base/items/${UUID.randomUUID()}/edit") { with(manager) }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `items of another business are out of reach`() {
        val rioja = createItem("Rioja")
        val other = jdbc.createVenue()
        val stranger = jdbc.createMember(other, Role.OWNER)

        mockMvc.get("$base/items/${rioja.id}/edit") {
            with(user(userDetailsService.loadUserByUsername("$stranger@test.passly")))
        }.andExpect { status { isForbidden() } }
    }
}
