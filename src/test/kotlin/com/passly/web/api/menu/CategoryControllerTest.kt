package com.passly.web.api.menu

import com.passly.web.api.StaffRole
import com.passly.web.api.createStation
import com.passly.TestcontainersConfiguration
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
import com.passly.web.api.mockMvcAs
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.*
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CategoryControllerTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    lateinit var mockMvc: MockMvc

    private lateinit var venueId: UUID
    private lateinit var stationId: UUID
    private val base get() = "/api/v1/venues/$venueId/categories"

    @BeforeEach
    fun createVenueWithManager() {
        val venue = jdbc.createVenue()
        venueId = venue.venueId
        stationId = jdbc.createStation(venue)
        mockMvc = context.mockMvcAs(jdbc.createMember(venue, StaffRole.MANAGER))
    }

    @Test
    fun `created categories are returned as a nested tree`() {
        val food = create("""{"name": "Food", "stationId": "$stationId"}""")
        create("""{"name": "Starters", "parentId": "$food"}""")

        mockMvc.get(base).andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("Food") }
            jsonPath("$[0].children[0].name") { value("Starters") }
        }
    }

    @Test
    fun `unchanged tree is answered with 304`() {
        create("""{"name": "Food", "stationId": "$stationId"}""")
        val etag = mockMvc.get(base).andReturn().response.getHeader("ETag")!!

        mockMvc.get(base) { header("If-None-Match", etag) }.andExpect { status { isNotModified() } }

        create("""{"name": "Drinks", "stationId": "$stationId"}""")
        mockMvc.get(base) { header("If-None-Match", etag) }.andExpect { status { isOk() } }
    }

    @Test
    fun `blank name is rejected with problem details`() {
        mockMvc.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "  "}"""
        }.andExpect {
            status { isBadRequest() }
            content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.errors.name") { exists() }
        }
    }

    @Test
    fun `a top-level category without a station is rejected`() {
        mockMvc.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Food"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("Choose a station for a top-level category") }
        }
    }

    @Test
    fun `moving a category into its own subcategory is a conflict`() {
        val food = create("""{"name": "Food", "stationId": "$stationId"}""")
        val mains = create("""{"name": "Mains", "parentId": "$food"}""")

        mockMvc.post("$base/$food/move") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"newParentId": "$mains"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.detail") { value("A category cannot be moved into itself or one of its subcategories") }
        }
    }

    @Test
    fun `update with a stale version is a conflict`() {
        val food = create("""{"name": "Food", "stationId": "$stationId"}""")

        mockMvc.put("$base/$food") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Kitchen", "stationId": "$stationId", "active": true, "version": 0}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.version") { value(1) }
        }

        mockMvc.put("$base/$food") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Food again", "stationId": "$stationId", "active": true, "version": 0}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `deleted category is no longer found`() {
        val food = create("""{"name": "Food", "stationId": "$stationId"}""")

        mockMvc.delete("$base/$food").andExpect { status { isNoContent() } }
        mockMvc.get("$base/$food").andExpect { status { isNotFound() } }
    }

    @Test
    fun `unknown venue is forbidden`() {
        mockMvc.get("/api/v1/venues/${UUID.randomUUID()}/categories").andExpect { status { isForbidden() } }
    }

    private fun create(json: String): String =
        mockMvc.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.getHeader("Location")!!.substringAfterLast('/')
}
