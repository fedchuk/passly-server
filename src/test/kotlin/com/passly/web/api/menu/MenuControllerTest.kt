package com.passly.web.api.menu

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.*
import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.CategoryId
import com.passly.domain.menu.MenuItem
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.ModifierDraft
import com.passly.domain.user.Role
import com.passly.domain.venue.VenueId
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
import com.passly.web.api.mockMvcAs
import com.passly.web.api.securedMockMvc
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MenuControllerTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var categories: CategoryService

    @Autowired
    lateinit var items: MenuItemService

    @Autowired
    lateinit var modifierGroups: ModifierGroupService

    lateinit var waiter: MockMvc
    lateinit var venueUuid: UUID
    private val venueId get() = VenueId(venueUuid)

    private val base get() = "/api/v1/venues/${venueId.value}/menu"

    @BeforeEach
    fun setUp() {
        val venue = jdbc.createVenue()
        venueUuid = venue.venueId
        waiter = context.mockMvcAs(jdbc.createMember(venue, Role.WAITER))
    }

    private fun category(name: String, parentId: CategoryId? = null) =
        categories.create(CreateCategoryCommand(venueId, name, parentId))

    private fun item(categoryId: CategoryId, name: String, price: String = "24.00"): MenuItem =
        items.create(
            CreateMenuItemCommand(
                venueId,
                categoryId,
                MenuItemDetails(name, null, Money.of(price), TaxRate.of("10"))
            )
        )

    @Test
    fun `waiter gets the whole active menu in one response`() {
        val food = category("Food")
        val steaks = category("Steaks", food.id)
        val ribeye = item(steaks.id, "Ribeye")
        val doneness = modifierGroups.create(
            CreateModifierGroupCommand(
                venueId,
                "Doneness",
                1,
                1,
                listOf(ModifierDraft("Rare", Money.of("0")), ModifierDraft("Medium", Money.of("0")))
            ),
        )
        items.attachModifierGroups(AttachModifierGroupsCommand(venueId, ribeye.id, listOf(doneness.id)))
        val hidden = category("Seasonal")
        categories.update(UpdateCategoryCommand(venueId, hidden.id, "Seasonal", null, active = false))

        waiter.get(base).andExpect {
            status { isOk() }
            jsonPath("$.categories") { value(hasSize<Any>(1)) }
            jsonPath("$.categories[0].name") { value("Food") }
            jsonPath("$.categories[0].children[0].items[0].name") { value("Ribeye") }
            jsonPath("$.categories[0].children[0].items[0].price") { value("24.00") }
            jsonPath("$.categories[0].children[0].items[0].modifierGroupIds[0]") { value(doneness.id.value.toString()) }
            jsonPath("$.modifierGroups[0].options[1].name") { value("Medium") }
        }
    }

    @Test
    fun `unchanged menu is answered with an empty 304`() {
        val ribeye = item(category("Steaks").id, "Ribeye")
        val etag = waiter.get(base).andReturn().response.getHeader("ETag")!!

        waiter.get(base) { header("If-None-Match", etag) }.andExpect { status { isNotModified() } }

        items.changeAvailability(venueId, ribeye.id, available = false)

        val response = waiter.get(base) { header("If-None-Match", etag) }.andExpect {
            status { isOk() }
            jsonPath("$.categories[0].items[0].available") { value(false) }
        }.andReturn().response
        assertNotEquals(etag, response.getHeader("ETag"))
    }

    @Test
    fun `etag is the menu version`() {
        category("Food")
        category("Drinks")

        val etag = waiter.get(base).andReturn().response.getHeader("ETag")

        assertEquals("\"2\"", etag)
    }

    @Test
    fun `menu needs a token`() {
        context.securedMockMvc().get("/api/v1/venues/${UUID.randomUUID()}/menu").andExpect { status { isUnauthorized() } }
    }
}
