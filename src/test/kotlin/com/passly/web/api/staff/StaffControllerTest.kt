package com.passly.web.api.staff

import com.passly.TestcontainersConfiguration
import com.passly.domain.role.RolePreset
import com.passly.web.api.*
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
    lateinit var venue: TestVenue

    private val base get() = "/api/v1/venues/${venue.venueId}/staff"
    private val email = "lucia-${UUID.randomUUID().toString().take(8)}@bistro.es"

    @BeforeEach
    fun createVenueWithOwner() {
        venue = jdbc.createVenue()
        managerMockMvc = context.mockMvcAs(jdbc.createMember(venue, StaffRole.MANAGER))
        mockMvc = context.mockMvcAs(jdbc.createMember(venue, StaffRole.OWNER))
    }

    private fun role(preset: RolePreset) = jdbc.roleId(venue, preset)

    private fun hire(roleId: UUID = role(RolePreset.WAITER), client: MockMvc = mockMvc) = client.post(base) {
        contentType = MediaType.APPLICATION_JSON
        content = """{"email": "$email", "displayName": "Lucia", "password": "password-1", "roleId": "$roleId"}"""
    }

    private fun hiredId(): String =
        hire().andExpect { status { isCreated() } }
            .andReturn().response.getHeader("Location")!!.substringAfterLast('/')

    @Test
    fun `hired waiter appears in the staff list`() {
        hire().andExpect {
            status { isCreated() }
            header { exists("Location") }
            jsonPath("$.roleId") { value(role(RolePreset.WAITER).toString()) }
            jsonPath("$.roleName") { value("Waiter") }
        }

        mockMvc.get(base).andExpect {
            status { isOk() }
            jsonPath("$[?(@.displayName == 'Lucia')].roleName") { value("Waiter") }
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
    fun `role of another venue is not found`() {
        val other = jdbc.createVenue()

        hire(jdbc.roleId(other, RolePreset.WAITER)).andExpect { status { isNotFound() } }
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
            content = """{"roleId": "${role(RolePreset.KITCHEN)}"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.roleName") { value("Kitchen") }
        }

        mockMvc.delete("$base/$lucia").andExpect { status { isNoContent() } }
        mockMvc.get(base).andExpect { jsonPath("$[?(@.userId == '$lucia')]") { isEmpty() } }
    }

    @Test
    fun `manager cannot see or hire the staff`() {
        managerMockMvc.get(base).andExpect { status { isForbidden() } }

        hire(client = managerMockMvc).andExpect { status { isForbidden() } }
    }

    @Test
    fun `nobody gives a role that allows more than their own`() {
        jdbc.sql("insert into venue_role_permission (role_id, permission) values (?, 'MANAGE_STAFF')")
            .params(role(RolePreset.WAITER))
            .update()
        val headWaiter = context.mockMvcAs(jdbc.createMember(venue, StaffRole.WAITER))

        hire(role(RolePreset.MANAGER), headWaiter).andExpect { status { isForbidden() } }
        hire(role(RolePreset.WAITER), headWaiter).andExpect { status { isCreated() } }
    }
}

