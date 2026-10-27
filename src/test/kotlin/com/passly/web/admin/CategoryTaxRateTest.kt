package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.application.tax.TaxRateService
import com.passly.domain.menu.StationId
import com.passly.domain.tax.VenueTaxRate
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
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CategoryTaxRateTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var categories: CategoryService

    @Autowired
    lateinit var taxRates: TaxRateService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var manager: RequestPostProcessor
    lateinit var kitchenUuid: UUID
    lateinit var food: VenueTaxRate
    lateinit var alcohol: VenueTaxRate

    private val venueId get() = VenueId(venue.venueId)
    private val page get() = "/admin/venues/${venue.venueId}"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        kitchenUuid = jdbc.createStation(venue)
        manager = user(userDetailsService.loadUserByUsername("${jdbc.createMember(venue, StaffRole.MANAGER)}@test.passly"))
        taxRates.addPresets(venueId)
        food = taxRates.list(venueId).single { it.name == "Food and soft drinks" }
        alcohol = taxRates.list(venueId).single { it.name == "Alcohol" }
    }

    @Test
    fun `new top-level category gets the chosen rate`() {
        mockMvc.post("$page/categories") {
            with(manager)
            with(csrf())
            param("name", "Wine")
            param("stationId", kitchenUuid.toString())
            param("taxRateId", alcohol.id.toString())
        }

        assertEquals(alcohol.id, categories.getTree(venueId).children(null).single().taxRateId)
    }

    @Test
    fun `menu shows the rate of a category and offers the rates when adding one`() {
        categories.create(
            CreateCategoryCommand(
                venueId,
                "Wine",
                stationId = StationId(kitchenUuid),
                taxRateId = alcohol.id
            )
        )

        mockMvc.get(page) { with(manager) }.andExpect {
            content { string(containsString("Alcohol · 20%")) }
            content { string(containsString("name=\"taxRateId\"")) }
        }
    }

    @Test
    fun `subcategory edit page offers the rate of the parent`() {
        val drinks = categories.create(CreateCategoryCommand(venueId, "Drinks", stationId = StationId(kitchenUuid), taxRateId = alcohol.id))
        val juice = categories.create(CreateCategoryCommand(venueId, "Juice", drinks.id))

        mockMvc.get("$page/categories/${juice.id}/edit") { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("Same as the parent (Alcohol · 20%)")) }
        }
    }

    @Test
    fun `subcategory gets its own rate on edit`() {
        val drinks = categories.create(CreateCategoryCommand(venueId, "Drinks", stationId = StationId(kitchenUuid), taxRateId = alcohol.id))
        val juice = categories.create(CreateCategoryCommand(venueId, "Juice", drinks.id))

        mockMvc.post("$page/categories/${juice.id}") {
            with(manager)
            with(csrf())
            param("name", "Juice")
            param("version", "0")
            param("active", "true")
            param("taxRateId", food.id.toString())
        }.andExpect { redirectedUrl("$page/categories/${juice.id}/items") }

        assertEquals(food.id, categories.getTree(venueId).effectiveTaxRate(juice.id))
    }

    @Test
    fun `api creates a category with a rate and reports the inherited one`() {
        val ownerApi = context.mockMvcAs(jdbc.createMember(venue, StaffRole.OWNER))
        val wineId = ownerApi.post("/api/v1/venues/${venue.venueId}/categories") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Wine", "stationId": "$kitchenUuid", "taxRateId": "${alcohol.id}"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.taxRateId") { value(alcohol.id.toString()) }
        }.andReturn().response.getHeader("Location")!!.substringAfterLast('/')

        ownerApi.post("/api/v1/venues/${venue.venueId}/categories") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Rosé", "parentId": "$wineId"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.taxRateId") { doesNotExist() }
            jsonPath("$.effectiveTaxRateId") { value(alcohol.id.toString()) }
        }
    }

    @Test
    fun `rate used by a category cannot be deleted`() {
        categories.create(CreateCategoryCommand(venueId, "Wine", stationId = StationId(kitchenUuid), taxRateId = alcohol.id))

        mockMvc.post("$page/settings/tax-rates/${alcohol.id}/delete") {
            with(manager)
            with(csrf())
        }.andExpect {
            flash { attribute("error", "Tax rate 'Alcohol' is used by categories. Choose another rate for them first.") }
        }
    }
}
