package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.menu.CreateMenuItemCommand
import com.passly.application.menu.MenuItemService
import com.passly.application.tax.ChangeTaxRateCommand
import com.passly.application.tax.TaxRateService
import com.passly.domain.common.Money
import com.passly.domain.common.TaxRate
import com.passly.domain.menu.Category
import com.passly.domain.menu.MenuItemDetails
import com.passly.domain.menu.StationId
import com.passly.domain.tax.VenueTaxRate
import com.passly.domain.user.Role
import com.passly.domain.venue.VenueId
import com.passly.web.api.*
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
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

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MenuItemTaxRateTest {

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
    lateinit var taxRates: TaxRateService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var manager: RequestPostProcessor
    lateinit var drinks: Category
    lateinit var wine: Category
    lateinit var food: VenueTaxRate
    lateinit var alcohol: VenueTaxRate
    lateinit var reduced: VenueTaxRate

    private val venueId get() = VenueId(venue.venueId)
    private val page get() = "/admin/venues/${venue.venueId}"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        manager = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, Role.MANAGER)}@test.passly"))
        drinks = categories.create(
            CreateCategoryCommand(
                venueId,
                "Drinks",
                stationId = StationId(jdbc.createStation(venue))
            )
        )
        val rates = taxRates.list(venueId)
        food = rates.single { it.name == "Food and soft drinks" }
        alcohol = rates.single { it.name == "Alcohol" }
        reduced = rates.single { it.name == "Reduced" }
        wine = categories.create(CreateCategoryCommand(venueId, "Wine", drinks.id, taxRateId = alcohol.id))
    }

    private fun dish(category: Category, name: String, rate: VenueTaxRate? = null) =
        items.create(
            CreateMenuItemCommand(
                venueId,
                category.id,
                MenuItemDetails(name, null, Money.of("6.00"), taxRateId = rate?.id)
            )
        )

    @Test
    fun `item form offers the rate of the category`() {
        mockMvc.get("$page/categories/${wine.id}/items/new") { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("Same as the category (Alcohol · 20%)")) }
            content { string(containsString("name=\"taxRateId\"")) }
        }
    }

    @Test
    fun `dish chosen its own rate keeps it and shows it in the menu`() {
        mockMvc.post("$page/categories/${wine.id}/items") {
            with(manager)
            with(csrf())
            param("name", "Alcohol-free red")
            param("price", "5.00")
            param("taxRateId", food.id.toString())
        }.andExpect { redirectedUrl("$page/categories/${wine.id}/items") }

        val stored = items.listInCategory(venueId, wine.id).single()
        assertEquals(food.id, stored.taxRateId)
        mockMvc.get("$page/categories/${wine.id}/items") { with(manager) }.andExpect {
            content { string(containsString(" · Food and soft drinks · 10%")) }
        }
    }

    @Test
    fun `dish left on the category rate stores no rate of its own`() {
        mockMvc.post("$page/categories/${wine.id}/items") {
            with(manager)
            with(csrf())
            param("name", "Rioja")
            param("price", "6.00")
            param("taxRateId", "")
        }

        assertEquals(null, items.listInCategory(venueId, wine.id).single().taxRateId)
    }

    @Test
    fun `waiter menu gives every dish the rate it is sold with`() {
        dish(wine, "Rioja")
        dish(wine, "Alcohol-free red", food)
        val waiter = context.mockMvcAs(jdbc.createMember(venue, Role.WAITER))

        waiter.get("/api/v1/venues/${venue.venueId}/menu").andExpect {
            status { isOk() }
            jsonPath("$.categories[0].children[0].items[0].taxRate") { value("20.00") }
            jsonPath("$.categories[0].children[0].items[1].taxRate") { value("10.00") }
        }
    }

    @Test
    fun `new percent of a rate reaches the waiter menu`() {
        dish(wine, "Rioja")
        val waiter = context.mockMvcAs(jdbc.createMember(venue, Role.WAITER))
        val etag = waiter.get("/api/v1/venues/${venue.venueId}/menu").andReturn().response.getHeader("ETag")!!

        taxRates.change(ChangeTaxRateCommand(venueId, alcohol.id, "Alcohol", TaxRate.of("21")))

        waiter.get("/api/v1/venues/${venue.venueId}/menu") { header("If-None-Match", etag) }.andExpect {
            status { isOk() }
            jsonPath("$.categories[0].children[0].items[0].taxRate") { value("21.00") }
        }
    }

    @Test
    fun `api stores the own rate of a dish and reports the effective one`() {
        val owner = context.mockMvcAs(jdbc.createMember(venue, Role.OWNER))
        val items = "/api/v1/venues/${venue.venueId}/categories/${wine.id}/items"

        owner.post(items) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Rioja", "price": "6.00"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.taxRateId") { doesNotExist() }
            jsonPath("$.effectiveTaxRateId") { value(alcohol.id.toString()) }
        }

        owner.post(items) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Grape juice", "price": "4.00", "taxRateId": "${reduced.id}"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.taxRateId") { value(reduced.id.toString()) }
            jsonPath("$.effectiveTaxRateId") { value(reduced.id.toString()) }
        }
    }

    @Test
    fun `rate used by a dish cannot be deleted`() {
        dish(wine, "Grape juice", reduced)

        mockMvc.post("$page/settings/tax-rates/${reduced.id}/delete") {
            with(manager)
            with(csrf())
        }.andExpect {
            flash { attribute("error", "Tax rate 'Reduced' is used by dishes. Choose another rate for them first.") }
        }
    }
}
