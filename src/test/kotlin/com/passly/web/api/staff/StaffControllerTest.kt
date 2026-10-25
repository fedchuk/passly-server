package com.passly.web.api.staff

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
class StaffControllerTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    lateinit var mockMvc: MockMvc
    lateinit var managerMockMvc: MockMvc

    private lateinit var venueId: UUID
    private val base get() = "/api/v1/venues/$venueId/staff"
    private val email = "lucia-${UUID.randomUUID().toString().take(8)}@bistro.es"

    @BeforeEach
    fun createVenueWithOwner() {
        val venue = jdbc.createVenue()
        venueId = venue.venueId
        managerMockMvc = context.mockMvcAs(jdbc.createMember(venue, Role.MANAGER))
        mockMvc = context.mockMvcAs(jdbc.createMember(venue, Role.OWNER))
    }

    private fun hire(role: String = "WAITER") = mockMvc.post(base) {
        contentType = MediaType.APPLICATION_JSON
        content = """{"email": "$email", "displayName": "Lucia", "password": "password-1", "role": "$role"}"""
    }

    private fun hiredId(): String =
        hire().andExpect { status { isCreated() } }
            .andReturn().response.getHeader("Location")!!.substringAfterLast('/')

    @Test
    fun `hired waiter appears in the staff list`() {
        hire().andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.role") { value("WAITER") }
        }

        mockMvc.get(base).andExpect {
            status { isOk() }
            jsonPath("$[?(@.displayName == 'Lucia')].role") { value("WAITER") }
        }
    }

    @Test
    fun `hired waiter can log in right away`() {
        hiredId()

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "$email", "password": "password-1"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `owner cannot be hired for one venue`() {
        hire("OWNER").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { exists() }
        }
    }

    @Test
    fun `the same email cannot be hired twice`() {
        hiredId()

        hire().andExpect { status { isConflict() } }
    }

    @Test
    fun `role can be changed and the member dismissed`() {
        val lucia = hiredId()

        mockMvc.put("$base/$lucia/role") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"role": "KITCHEN"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("KITCHEN") }
        }

        mockMvc.delete("$base/$lucia").andExpect { status { isNoContent() } }
        mockMvc.get(base).andExpect { jsonPath("$[?(@.userId == '$lucia')]") { isEmpty() } }
    }

    @Test
    fun `manager cannot see or hire the staff`() {
        managerMockMvc.get(base).andExpect { status { isForbidden() } }

        managerMockMvc.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "$email", "displayName": "Lucia", "password": "password-1", "role": "WAITER"}"""
        }.andExpect { status { isForbidden() } }
    }
}

