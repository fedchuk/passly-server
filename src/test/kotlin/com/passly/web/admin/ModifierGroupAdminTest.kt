package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.*
import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.ModifierDraft
import com.passly.domain.menu.ModifierGroup
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
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ModifierGroupAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var groups: ModifierGroupService

    @Autowired
    lateinit var categories: CategoryService

    @Autowired
    lateinit var items: MenuItemService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var manager: RequestPostProcessor

    private val venueId get() = VenueId(venue.venueId)
    private val base get() = "/admin/venues/${venue.venueId}/modifier-groups"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        val managerId = jdbc.createMember(venue, Role.MANAGER)
        manager = user(userDetailsService.loadUserByUsername("$managerId@test.passly"))
    }

    private fun createDoneness(): ModifierGroup = groups.create(
        CreateModifierGroupCommand(
            venueId, "Doneness", 1, 1,
            listOf(ModifierDraft("Rare", Money.of("0")), ModifierDraft("Well done", Money.of("0.50"))),
        ),
    )

    @Test
    fun `list shows the rule and the options with prices`() {
        createDoneness()

        mockMvc.get(base) { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("Doneness")) }
            content { string(containsString("Choose 1")) }
            content { string(containsString("Rare, Well done (+0.50)")) }
        }
    }

    @Test
    fun `new group is created and empty option rows are ignored`() {
        mockMvc.post(base) {
            with(manager)
            with(csrf())
            param("name", "Sauce")
            param("minSelect", "0")
            param("maxSelect", "2")
            param("options[0].name", "Garlic")
            param("options[0].priceDelta", "")
            param("options[1].name", "No sauce")
            param("options[1].priceDelta", "-0,50")
            param("options[2].name", "")
            param("options[2].priceDelta", "")
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Modifier group 'Sauce' added") }
        }

        val sauce = groups.list(venueId).single()
        assertEquals(listOf("Garlic", "No sauce"), sauce.modifiers.map { it.name })
        assertEquals(Money.of("-0.50"), sauce.modifiers[1].priceDelta)
    }

    @Test
    fun `impossible limits show the form again`() {
        mockMvc.post(base) {
            with(manager)
            with(csrf())
            param("name", "Sides")
            param("minSelect", "3")
            param("maxSelect", "3")
            param("options[0].name", "Fries")
        }.andExpect {
            status { isOk() }
            view { name("admin/modifier-group-form") }
            model { attributeHasErrors("form") }
        }

        assertTrue(groups.list(venueId).isEmpty())
    }

    @Test
    fun `text in a number field gets a readable error`() {
        mockMvc.post(base) {
            with(manager)
            with(csrf())
            param("name", "Sides")
            param("minSelect", "one")
            param("maxSelect", "1")
            param("options[0].name", "Fries")
        }.andExpect {
            status { isOk() }
            model { attributeHasFieldErrors("form", "minSelect") }
            content { string(containsString("Enter a whole number")) }
        }
    }

    @Test
    fun `edit keeps option ids, reorders and removes options`() {
        val doneness = createDoneness()
        val (rare, wellDone) = doneness.modifiers

        mockMvc.post("$base/${doneness.id}") {
            with(manager)
            with(csrf())
            param("name", "Doneness")
            param("minSelect", "1")
            param("maxSelect", "1")
            param("version", "0")
            param("options[0].id", wellDone.id.toString())
            param("options[0].name", "Well done")
            param("options[0].priceDelta", "0.50")
            param("options[2].name", "Medium")
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Modifier group 'Doneness' saved") }
        }

        val stored = groups.get(venueId, doneness.id)
        assertEquals(listOf("Well done", "Medium"), stored.modifiers.map { it.name })
        assertEquals(wellDone.id, stored.modifiers[0].id)
        assertTrue(stored.modifiers.none { it.id == rare.id })
    }

    @Test
    fun `stale version shows the form again with an error`() {
        val doneness = createDoneness()

        mockMvc.post("$base/${doneness.id}") {
            with(manager)
            with(csrf())
            param("name", "Doneness")
            param("minSelect", "1")
            param("maxSelect", "1")
            param("version", "5")
            param("options[0].name", "Rare")
        }.andExpect {
            status { isOk() }
            content { string(containsString("changed by someone else")) }
        }
    }

    @Test
    fun `unused group is deleted`() {
        val doneness = createDoneness()

        mockMvc.post("$base/${doneness.id}/delete") {
            with(manager)
            with(csrf())
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Modifier group 'Doneness' deleted") }
        }

        assertTrue(groups.list(venueId).isEmpty())
    }

    @Test
    fun `group used by a menu item is kept and an error is shown`() {
        val doneness = createDoneness()
        val steaks = categories.create(CreateCategoryCommand(venueId, "Steaks")).id
        val ribeye = items.create(
            CreateMenuItemCommand(venueId, steaks, MenuItemDetails("Ribeye", null, Money.of("24"), TaxRate.of("10"))),
        )
        items.attachModifierGroups(AttachModifierGroupsCommand(venueId, ribeye.id, listOf(doneness.id)))

        mockMvc.post("$base/${doneness.id}/delete") {
            with(manager)
            with(csrf())
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("error", "Modifier group 'Doneness' is used by menu items. Detach it first.") }
        }

        assertEquals(1, groups.list(venueId).size)
    }
}
