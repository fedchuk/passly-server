package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.*
import com.passly.domain.common.Money
import com.passly.domain.menu.*
import com.passly.domain.venue.VenueId
import com.passly.web.api.StaffRole
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
import java.util.*
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
        val managerId = jdbc.createMember(venue, StaffRole.MANAGER)
        manager = user(userDetailsService.loadUserByUsername("$managerId@test.passly"))
        wineId = categories.create(CreateCategoryCommand(venueId, "Wine", stationId = createStation("Kitchen"))).id.value
    }

    private fun createItem(name: String, price: String = "4.50"): MenuItem =
        items.create(
            CreateMenuItemCommand(
                venueId, wine,
                MenuItemDetails(name, null, Money.of(price))
            )
        )

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
            param("version", "0")
            param("modifierGroupIds", size.id.toString())
        }

        mockMvc.post("$base/items/${rioja.id}") {
            with(manager)
            with(csrf())
            param("name", "Rioja Reserva")
            param("description", "Tempranillo, 3 years")
            param("price", "6")
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
    fun `items are put in the dragged order`() {
        val rioja = createItem("Rioja")
        val albarino = createItem("Albariño")
        val verdejo = createItem("Verdejo")

        mockMvc.post("$wineItems/order") {
            with(manager)
            with(csrf())
            param("orderedIds", verdejo.id.toString(), rioja.id.toString(), albarino.id.toString())
        }.andExpect { status { isNoContent() } }

        assertEquals(listOf("Verdejo", "Rioja", "Albariño"), names())
    }

    @Test
    fun `order that misses an item is refused`() {
        val rioja = createItem("Rioja")
        createItem("Albariño")

        mockMvc.post("$wineItems/order") {
            with(manager)
            with(csrf())
            param("orderedIds", rioja.id.toString())
        }.andExpect { status { isEqualTo(422) } }

        assertEquals(listOf("Rioja", "Albariño"), names())
    }

    @Test
    fun `order without a csrf token is rejected`() {
        val rioja = createItem("Rioja")

        mockMvc.post("$wineItems/order") {
            with(manager)
            param("orderedIds", rioja.id.toString())
        }.andExpect { status { isForbidden() } }
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
        val stranger = jdbc.createMember(other, StaffRole.OWNER)

        mockMvc.get("$base/items/${rioja.id}/edit") {
            with(user(userDetailsService.loadUserByUsername("$stranger@test.passly")))
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `edit page offers every category with the current one selected`() {
        val reds = categories.create(CreateCategoryCommand(venueId, "Reds", parentId = wine)).id
        val beer = categories.create(CreateCategoryCommand(venueId, "Beer", stationId = createStation("Beer tap"))).id
        val rioja = createItem("Rioja")

        mockMvc.get("$base/items/${rioja.id}/edit") { with(manager) }.andExpect {
            status { isOk() }
            model {
                attribute(
                    "categoryOptions",
                    listOf(
                        ParentOption(wine.value, "Wine"),
                        ParentOption(reds.value, "— Reds"),
                        ParentOption(beer.value, "Beer"),
                    ),
                )
                attribute("currentCategoryId", wine.value)
            }
        }
    }

    @Test
    fun `item moves to another category`() {
        val reds = categories.create(CreateCategoryCommand(venueId, "Reds", parentId = wine)).id
        val rioja = createItem("Rioja")

        mockMvc.post("$base/items/${rioja.id}/move") {
            with(manager)
            with(csrf())
            param("categoryId", reds.toString())
        }.andExpect {
            redirectedUrl("$base/categories/$reds/items")
            flash { attribute("message", "'Rioja' moved to Reds") }
        }

        assertTrue(names().isEmpty())
        assertEquals(listOf("Rioja"), items.listInCategory(venueId, reds).map { it.name })
    }

    @Test
    fun `moving to an unknown category shows the not found page`() {
        val rioja = createItem("Rioja")

        mockMvc.post("$base/items/${rioja.id}/move") {
            with(manager)
            with(csrf())
            param("categoryId", UUID.randomUUID().toString())
        }.andExpect { status { isNotFound() } }

        assertEquals(listOf("Rioja"), names())
    }
}
