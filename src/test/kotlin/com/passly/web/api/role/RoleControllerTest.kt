package com.passly.web.api.role

import com.passly.TestcontainersConfiguration
import com.passly.domain.role.RolePreset
import com.passly.web.api.StaffRole
import com.passly.web.api.TestVenue
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
import com.passly.web.api.mockMvcAs
import com.passly.web.api.roleId
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.web.context.WebApplicationContext
import java.util.UUID
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class RoleControllerTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    lateinit var venue: TestVenue
    lateinit var owner: MockMvc
    lateinit var waiter: UUID

    private val base get() = "/api/v1/venues/${venue.venueId}/roles"

    @BeforeEach
    fun setUp() {
        venue = jdbc.createVenue()
        waiter = jdbc.roleId(venue, RolePreset.WAITER)
        owner = context.mockMvcAs(jdbc.createMember(venue, StaffRole.OWNER))
    }

    @Test
    fun `owner lists the roles with their permissions and people`() {
        jdbc.createMember(venue, StaffRole.WAITER)

        owner.get(base).andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("Waiter") }
            jsonPath("$[0].permissions") { value(containsInAnyOrder("VIEW_MENU", "TAKE_ORDERS", "CLOSE_ORDERS")) }
            jsonPath("$[0].members") { value(1) }
        }
    }

    @Test
    fun `role is created, updated with its version and deleted`() {
        val location = owner.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Host", "permissions": ["TAKE_ORDERS"]}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.permissions") { value(containsInAnyOrder("VIEW_MENU", "TAKE_ORDERS")) }
            jsonPath("$.version") { value(0) }
        }.andReturn().response.getHeader("Location")!!

        owner.put(location) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Hostess", "permissions": ["TAKE_ORDERS", "MANAGE_TABLES"], "version": 0}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Hostess") }
            jsonPath("$.version") { value(1) }
        }

        owner.put(location) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Hostess", "permissions": [], "version": 0}"""
        }.andExpect { status { isConflict() } }

        owner.delete(location).andExpect { status { isNoContent() } }
        owner.get(location).andExpect { status { isNotFound() } }
    }

    @Test
    fun `owner only permission and owner name are refused`() {
        owner.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Boss", "permissions": ["MANAGE_ROLES"]}"""
        }.andExpect { status { isBadRequest() } }

        owner.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "owner"}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `role with people is not deleted`() {
        jdbc.createMember(venue, StaffRole.WAITER)

        owner.delete("$base/$waiter").andExpect { status { isConflict() } }
    }

    @Test
    fun `staff manager reads the roles but only the owner changes them`() {
        jdbc.sql("insert into venue_role_permission (role_id, permission) values (?, 'MANAGE_STAFF')").params(waiter).update()
        val headWaiter = context.mockMvcAs(jdbc.createMember(venue, StaffRole.WAITER))
        val manager = context.mockMvcAs(jdbc.createMember(venue, StaffRole.MANAGER))

        headWaiter.get(base).andExpect { status { isOk() } }
        manager.get(base).andExpect { status { isForbidden() } }
        headWaiter.post(base) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Host"}"""
        }.andExpect { status { isForbidden() } }
        headWaiter.delete("$base/$waiter").andExpect { status { isForbidden() } }
    }

    @Test
    fun `profile lists the venues with the role and its permissions`() {
        val cook = context.mockMvcAs(jdbc.createMember(venue, StaffRole.KITCHEN))

        cook.get("/api/v1/me").andExpect {
            status { isOk() }
            jsonPath("$.venues[0].role") { value("Kitchen") }
            jsonPath("$.venues[0].owner") { value(false) }
            jsonPath("$.venues[0].permissions") { value(containsInAnyOrder("VIEW_MENU", "MANAGE_STOP_LIST", "PREPARE_ORDERS")) }
        }
        owner.get("/api/v1/me").andExpect {
            jsonPath("$.venues[0].owner") { value(true) }
            jsonPath("$.venues[0].role") { value("Owner") }
        }
    }
}
