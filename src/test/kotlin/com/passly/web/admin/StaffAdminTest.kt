package com.passly.web.admin

import com.passly.web.api.doesNotContain
import com.passly.TestcontainersConfiguration
import com.passly.application.user.HireStaffCommand
import com.passly.application.user.StaffService
import com.passly.domain.role.RolePreset
import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.web.api.StaffRole
import com.passly.web.api.TestVenue
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
import com.passly.web.api.roleId
import com.passly.web.api.securedMockMvc
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
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class StaffAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var staff: StaffService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue
    lateinit var ownerId: UUID
    lateinit var owner: RequestPostProcessor

    private val venueId get() = VenueId(venue.venueId)
    private val base get() = "/admin/venues/${venue.venueId}/staff"
    private val unique = UUID.randomUUID().toString().take(8)

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        ownerId = jdbc.createMember(venue, StaffRole.OWNER)
        owner = loggedIn(ownerId)
    }

    private fun loggedIn(userId: UUID) = user(userDetailsService.loadUserByUsername("$userId@test.passly"))

    private fun email(name: String) = "$name-$unique@bistro.es"

    private fun role(preset: RolePreset) = jdbc.roleId(venue, preset)

    private fun hireLucia(preset: RolePreset = RolePreset.WAITER) = staff.hire(
        HireStaffCommand(venueId, Email.of(email("lucia")), "Lucia", RawPassword.of("password-1"),
            VenueRoleId(role(preset))
        ),
    )

    private fun roleOf(userId: UserId) = staff.list(venueId).single { it.userId == userId }.roleName

    @Test
    fun `owner sees the staff with their roles`() {
        hireLucia()

        mockMvc.get(base) { with(owner) }.andExpect {
            status { isOk() }
            content { string(containsString("Lucia")) }
            content { string(containsString("Waiter")) }
        }
    }

    @Test
    fun `new cook is hired with a role of the venue and can log in`() {
        mockMvc.post(base) {
            with(owner)
            with(csrf())
            param("displayName", "Pablo")
            param("email", email("pablo"))
            param("password", "password-1")
            param("roleId", role(RolePreset.KITCHEN).toString())
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Pablo joined as Kitchen") }
        }

        val pablo = staff.list(venueId).single { it.displayName == "Pablo" }
        assertEquals("Kitchen", pablo.roleName)
        userDetailsService.loadUserByUsername(email("pablo"))
    }

    @Test
    fun `role must be chosen`() {
        mockMvc.post(base) {
            with(owner)
            with(csrf())
            param("displayName", "Pablo")
            param("email", email("pablo"))
            param("password", "password-1")
            param("roleId", "")
        }.andExpect {
            status { isOk() }
            model { attributeHasFieldErrors("form", "roleId") }
        }
    }

    @Test
    fun `invalid email and short password are shown next to the fields`() {
        mockMvc.post(base) {
            with(owner)
            with(csrf())
            param("displayName", "Pablo")
            param("email", "pablo")
            param("password", "short")
            param("roleId", role(RolePreset.WAITER).toString())
        }.andExpect {
            status { isOk() }
            view { name("admin/staff-form") }
            model { attributeHasFieldErrors("form", "email", "password") }
            content { string(doesNotContain("value=\"short\"")) }
        }
    }

    @Test
    fun `email of an existing user is rejected`() {
        hireLucia()

        mockMvc.post(base) {
            with(owner)
            with(csrf())
            param("displayName", "Lucia again")
            param("email", email("lucia"))
            param("password", "password-1")
            param("roleId", role(RolePreset.WAITER).toString())
        }.andExpect {
            status { isOk() }
            model { attributeHasFieldErrors("form", "email") }
        }
    }

    @Test
    fun `role is changed`() {
        val lucia = hireLucia()

        mockMvc.post("$base/${lucia.userId}/role") {
            with(owner)
            with(csrf())
            param("roleId", role(RolePreset.KITCHEN).toString())
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Lucia is now Kitchen") }
        }

        assertEquals("Kitchen", roleOf(lucia.userId))
    }

    @Test
    fun `dismissed member leaves the list`() {
        val lucia = hireLucia()

        mockMvc.post("$base/${lucia.userId}/dismiss") {
            with(owner)
            with(csrf())
        }.andExpect { redirectedUrl(base) }

        assertTrue(staff.list(venueId).none { it.userId == lucia.userId })
    }

    @Test
    fun `owner cannot dismiss themselves or change their own role`() {
        mockMvc.post("$base/$ownerId/dismiss") {
            with(owner)
            with(csrf())
        }.andExpect { flash { attribute("error", "You cannot dismiss yourself") } }

        mockMvc.post("$base/$ownerId/role") {
            with(owner)
            with(csrf())
            param("roleId", role(RolePreset.WAITER).toString())
        }.andExpect { flash { attribute("error", "You cannot change your own role") } }
    }

    @Test
    fun `manager and waiter cannot open the staff page`() {
        val manager = jdbc.createMember(venue, StaffRole.MANAGER)
        val waiter = jdbc.createMember(venue, StaffRole.WAITER)

        mockMvc.get(base) { with(loggedIn(manager)) }.andExpect { status { isForbidden() } }
        mockMvc.get(base) { with(loggedIn(waiter)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `staff manager hires only into roles they could do themselves`() {
        jdbc.sql("insert into venue_role_permission (role_id, permission) values (?, 'MANAGE_STAFF')")
            .params(role(RolePreset.WAITER))
            .update()
        val headWaiter = loggedIn(jdbc.createMember(venue, StaffRole.WAITER))

        mockMvc.get("$base/new") { with(headWaiter) }.andExpect {
            status { isOk() }
            content { string(containsString(">Waiter<")) }
            content { string(doesNotContain(">Manager<")) }
        }

        mockMvc.post(base) {
            with(headWaiter)
            with(csrf())
            param("displayName", "Pablo")
            param("email", email("pablo"))
            param("password", "password-1")
            param("roleId", role(RolePreset.MANAGER).toString())
        }.andExpect {
            status { isOk() }
            model { attributeHasFieldErrors("form", "roleId") }
        }

        assertTrue(staff.list(venueId).none { it.displayName == "Pablo" })
    }
}
