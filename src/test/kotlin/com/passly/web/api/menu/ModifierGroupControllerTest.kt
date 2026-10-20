package com.passly.web.api.menu

import com.jayway.jsonpath.JsonPath
import com.passly.TestcontainersConfiguration
import com.passly.domain.user.Role
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
class ModifierGroupControllerTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    lateinit var mockMvc: MockMvc

    private lateinit var venueId: UUID
    private val base get() = "/api/v1/venues/$venueId/modifier-groups"

    @BeforeEach
    fun createVenueWithManager() {
        val venue = jdbc.createVenue()
        venueId = venue.venueId
        mockMvc = context.mockMvcAs(jdbc.createMember(venue, Role.MANAGER))
    }

    @Test
    fun `created group is listed with its options`() {
        create("""{"name": "Extras", "minSelect": 0, "maxSelect": 2,
                   "options": [{"name": "Cheese", "priceDelta": "1"}, {"name": "No onion", "priceDelta": "-0.2"}]}""")

        mockMvc.get(base).andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("Extras") }
            jsonPath("$[0].options[1].name") { value("No onion") }
            jsonPath("$[0].options[1].priceDelta") { value("-0.20") }
        }
    }

    @Test
    fun `limits that cannot be satisfied are rejected`() {
        mockMvc.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Sauces", "minSelect": 3, "maxSelect": 3, "options": [{"name": "BBQ"}, {"name": "Mayo"}]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { exists() }
        }
    }

    @Test
    fun `invalid option is rejected with field errors`() {
        mockMvc.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Extras", "minSelect": 0, "maxSelect": 1, "options": [{"name": " ", "priceDelta": "1.555"}]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors") { exists() }
        }
    }

    @Test
    fun `update keeps option ids and rejects a stale version`() {
        val id = create("""{"name": "Size", "minSelect": 1, "maxSelect": 1, "options": [{"name": "Small"}]}""")
        val small = JsonPath.read<String>(mockMvc.get("$base/$id").andReturn().response.contentAsString, "$.options[0].id")
        val body = """{"name": "Size", "minSelect": 1, "maxSelect": 1,
                       "options": [{"id": "$small", "name": "Small"}, {"name": "Large", "priceDelta": "1.50"}], "version": 0}"""

        mockMvc.put("$base/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.options[0].id") { value(small) }
            jsonPath("$.options[1].name") { value("Large") }
            jsonPath("$.version") { value(1) }
        }

        mockMvc.put("$base/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `deleted group is not found`() {
        val id = create("""{"name": "Size", "minSelect": 1, "maxSelect": 1, "options": [{"name": "Small"}]}""")

        mockMvc.delete("$base/$id").andExpect { status { isNoContent() } }
        mockMvc.get("$base/$id").andExpect { status { isNotFound() } }
    }

    @Test
    fun `groups of an unknown venue are forbidden`() {
        mockMvc.get("/api/v1/venues/${UUID.randomUUID()}/modifier-groups").andExpect { status { isForbidden() } }
    }

    private fun create(json: String): String =
        mockMvc.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.getHeader("Location")!!.substringAfterLast('/')
}
