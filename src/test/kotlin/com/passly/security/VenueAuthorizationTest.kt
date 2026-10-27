package com.passly.security

import com.passly.TestcontainersConfiguration
import com.passly.domain.user.Role
import com.passly.web.api.*
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class VenueAuthorizationTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var stationUuid: UUID

    private val categories get() = "/api/v1/venues/${venue.venueId}/categories"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        stationUuid = jdbc.createStation(venue)
    }

    private fun member(role: Role, at: TestVenue = venue) = jdbc.createMember(at, role)

    private fun createCategory(userId: UUID) = mockMvc.post(categories) {
        with(asUser(userId))
        contentType = MediaType.APPLICATION_JSON
        content = """{"name": "Wine", "stationId": "$stationUuid"}"""
    }

    private fun createdId(userId: UUID, url: String, json: String): String =
        mockMvc.post(url) {
            with(asUser(userId))
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andExpect { status { isCreated() } }
            .andReturn().response.getHeader("Location")!!.substringAfterLast('/')

    @Test
    fun `request without a token is unauthorized`() {
        mockMvc.get(categories).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `waiter reads the menu but cannot change it`() {
        val waiter = member(Role.WAITER)

        mockMvc.get(categories) { with(asUser(waiter)) }.andExpect { status { isOk() } }
        createCategory(waiter).andExpect { status { isForbidden() } }
    }

    @Test
    fun `kitchen manages the stop list but cannot edit dishes`() {
        val manager = member(Role.MANAGER)
        val kitchen = member(Role.KITCHEN)
        val wine = createdId(manager, categories, """{"name": "Wine", "stationId": "$stationUuid"}""")
        val rioja = createdId(
            manager,
            "/api/v1/venues/${venue.venueId}/categories/$wine/items",
            """{"name": "Rioja", "price": "4.50"}""",
        )
        val item = "/api/v1/venues/${venue.venueId}/items/$rioja"

        mockMvc.put("$item/availability") {
            with(asUser(kitchen))
            contentType = MediaType.APPLICATION_JSON
            content = """{"available": false}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("$item/move") {
            with(asUser(kitchen))
            contentType = MediaType.APPLICATION_JSON
            content = """{"categoryId": "$wine"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `owner manages every venue of the business`() {
        val owner = member(Role.OWNER)

        createCategory(owner).andExpect { status { isCreated() } }
    }

    @Test
    fun `nobody from another business gets in`() {
        val competitorOwner = member(Role.OWNER, at = jdbc.createVenue())

        mockMvc.get(categories) { with(asUser(competitorOwner)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `token of a deleted or unknown user opens nothing`() {
        mockMvc.get(categories) { with(asUser(UUID.randomUUID())) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `only the owner sees the staff`() {
        val waiter = member(Role.WAITER)
        val manager = member(Role.MANAGER)
        val owner = member(Role.OWNER)
        val staff = "/api/v1/venues/${venue.venueId}/staff"

        mockMvc.get(staff) { with(asUser(waiter)) }.andExpect { status { isForbidden() } }
        mockMvc.get(staff) { with(asUser(manager)) }.andExpect { status { isForbidden() } }
        mockMvc.get(staff) { with(asUser(owner)) }.andExpect { status { isOk() } }
    }
}
