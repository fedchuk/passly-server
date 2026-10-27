package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.role.RenameRoleCommand
import com.passly.application.role.RoleService
import com.passly.domain.role.RolePreset
import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Permission
import com.passly.domain.venue.VenueId
import com.passly.web.api.*
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class RoleAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var roles: RoleService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var owner: RequestPostProcessor
    lateinit var manager: UUID
    lateinit var waiter: UUID
    lateinit var kitchen: UUID

    private val venueId get() = VenueId(venue.venueId)
    private val base get() = "/admin/venues/${venue.venueId}/roles"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        manager = jdbc.roleId(venue, RolePreset.MANAGER)
        waiter = jdbc.roleId(venue, RolePreset.WAITER)
        kitchen = jdbc.roleId(venue, RolePreset.KITCHEN)
        owner = loggedIn(jdbc.createMember(venue, StaffRole.OWNER))
    }

    private fun loggedIn(userId: UUID) = user(userDetailsService.loadUserByUsername("$userId@test.passly"))

    private fun role(id: UUID) = roles.get(venueId, VenueRoleId(id)).role

    private fun versions() = roles.list(venueId).map { "${it.role.id}:${it.role.version}" }

    @Test
    fun `owner sees a table of permissions with a column per role`() {
        jdbc.createMember(venue, StaffRole.WAITER)

        mockMvc.get(base) { with(owner) }.andExpect {
            status { isOk() }
            content { string(containsString("Manager")) }
            content { string(containsString("Kitchen")) }
            content { string(containsString("1 person")) }
            content { string(containsString("Take orders")) }
            content { string(containsString("value=\"$waiter:TAKE_ORDERS\"")) }
            content { string(containsString("aria-label=\"Waiter: Take orders\" checked")) }
            content { string(containsString("value=\"$kitchen:TAKE_ORDERS\"")) }
            content { string(doesNotContain("aria-label=\"Kitchen: Take orders\" checked")) }
            content { string(doesNotContain("MANAGE_ROLES")) }
        }
    }

    @Test
    fun `only the owner opens the roles and sees the link`() {
        val managerUser = loggedIn(jdbc.createMember(venue, StaffRole.MANAGER))

        mockMvc.get(base) { with(managerUser) }.andExpect { status { isForbidden() } }
        mockMvc.get("/admin/venues/${venue.venueId}/overview") { with(managerUser) }.andExpect {
            content { string(doesNotContain("/roles\"")) }
        }
        mockMvc.get("/admin/venues/${venue.venueId}/overview") { with(owner) }.andExpect {
            content { string(containsString("/admin/venues/${venue.venueId}/roles\"")) }
        }
    }

    @Test
    fun `ticked boxes become the permissions and work at once`() {
        val cook = loggedIn(jdbc.createMember(venue, StaffRole.WAITER))
        mockMvc.get("/kitchen/venues/${venue.venueId}/stop-list") { with(cook) }.andExpect { status { isForbidden() } }

        mockMvc.post("$base/permissions") {
            with(owner)
            with(csrf())
            versions().forEach { param("roles", it) }
            param("grants", "$waiter:TAKE_ORDERS", "$waiter:MANAGE_STOP_LIST", "$kitchen:PREPARE_ORDERS")
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Permissions saved") }
        }

        assertEquals(setOf(Permission.VIEW_MENU, Permission.TAKE_ORDERS, Permission.MANAGE_STOP_LIST), role(waiter).permissions)
        assertEquals(setOf(Permission.VIEW_MENU, Permission.PREPARE_ORDERS), role(kitchen).permissions)
        assertEquals(setOf(Permission.VIEW_MENU), role(manager).permissions)
        mockMvc.get("/kitchen/venues/${venue.venueId}/stop-list") { with(cook) }.andExpect { status { isOk() } }
    }

    @Test
    fun `stale table is refused and nothing changes`() {
        val stale = versions()
        roles.rename(RenameRoleCommand(venueId, VenueRoleId(waiter), "Server"))

        mockMvc.post("$base/permissions") {
            with(owner)
            with(csrf())
            stale.forEach { param("roles", it) }
            param("grants", "$waiter:EDIT_MENU")
        }.andExpect {
            flash { attribute("error", "Role 'Server' was changed by someone else. Reload and try again.") }
        }

        assertFalse(role(waiter).allows(Permission.EDIT_MENU))
    }

    @Test
    fun `owner only permission cannot be sent from the form`() {
        mockMvc.post("$base/permissions") {
            with(owner)
            with(csrf())
            versions().forEach { param("roles", it) }
            param("grants", "$waiter:MANAGE_ROLES")
        }.andExpect { flash { attribute("error", "Only owners can have MANAGE_ROLES") } }
    }

    @Test
    fun `new role copies another one and can be renamed and deleted`() {
        mockMvc.post(base) {
            with(owner)
            with(csrf())
            param("name", "Bartender")
            param("copyOf", waiter.toString())
        }.andExpect { flash { attribute("message", "Role 'Bartender' added. Tick what it may do and save.") } }

        val bartender = roles.list(venueId).single { it.role.name == "Bartender" }.role
        assertEquals(role(waiter).permissions, bartender.permissions)

        mockMvc.post("$base/${bartender.id}/rename") {
            with(owner)
            with(csrf())
            param("name", "Barista")
        }.andExpect { flash { attribute("message", "Role renamed to 'Barista'") } }

        mockMvc.post("$base/${bartender.id}/delete") {
            with(owner)
            with(csrf())
        }.andExpect { flash { attribute("message", "Role 'Barista' deleted") } }

        assertTrue(roles.list(venueId).none { it.role.id == bartender.id })
    }

    @Test
    fun `role with people cannot be deleted and owner is a reserved name`() {
        jdbc.createMember(venue, StaffRole.KITCHEN)

        mockMvc.post("$base/$kitchen/delete") {
            with(owner)
            with(csrf())
        }.andExpect { flash { attribute("error", "Role 'Kitchen' is given to 1 person. Give them another role first.") } }

        mockMvc.post(base) {
            with(owner)
            with(csrf())
            param("name", "Owner")
        }.andExpect { flash { attribute("error", "'Owner' is reserved for the owners of the business") } }
    }
}
