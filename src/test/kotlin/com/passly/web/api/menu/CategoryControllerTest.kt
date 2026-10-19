package com.passly.web.api.menu

import com.passly.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class CategoryControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jdbc: JdbcClient

    private val venueId = UUID.randomUUID()
    private val base get() = "/api/v1/venues/$venueId/categories"

    @BeforeEach
    fun createVenue() {
        val tenantId = UUID.randomUUID()
        jdbc.sql("insert into tenant (id, name) values (?, ?)").params(tenantId, "Test tenant").update()
        jdbc.sql("insert into venue (id, tenant_id, name) values (?, ?, ?)").params(venueId, tenantId, "Test venue").update()
    }

    @Test
    fun `created categories are returned as a nested tree`() {
        val food = create("""{"name": "Food"}""")
        create("""{"name": "Starters", "parentId": "$food"}""")

        mockMvc.get(base).andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("Food") }
            jsonPath("$[0].children[0].name") { value("Starters") }
        }
    }

    @Test
    fun `unchanged tree is answered with 304`() {
        create("""{"name": "Food"}""")
        val etag = mockMvc.get(base).andReturn().response.getHeader("ETag")!!

        mockMvc.get(base) { header("If-None-Match", etag) }.andExpect { status { isNotModified() } }

        create("""{"name": "Drinks"}""")
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
        }
    }

    @Test
    fun `moving a category into its own subcategory is a conflict`() {
        val food = create("""{"name": "Food"}""")
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
        val food = create("""{"name": "Food"}""")

        mockMvc.put("$base/$food") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Kitchen", "active": true, "version": 0}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.version") { value(1) }
        }

        mockMvc.put("$base/$food") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Food again", "active": true, "version": 0}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `deleted category is no longer found`() {
        val food = create("""{"name": "Food"}""")

        mockMvc.delete("$base/$food").andExpect { status { isNoContent() } }
        mockMvc.get("$base/$food").andExpect { status { isNotFound() } }
    }

    @Test
    fun `unknown venue is not found`() {
        mockMvc.get("/api/v1/venues/${UUID.randomUUID()}/categories").andExpect { status { isNotFound() } }
    }

    private fun create(json: String): String =
        mockMvc.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = json
        }.andExpect {
            status { isCreated() }
        }.andReturn().response.getHeader("Location")!!.substringAfterLast('/')
}
