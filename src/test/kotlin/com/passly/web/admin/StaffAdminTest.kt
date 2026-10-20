package com.passly.web.admin

import com.passly.TestcontainersConfiguration
import com.passly.application.user.HireStaffCommand
import com.passly.application.user.StaffService
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.Role
import com.passly.domain.user.UserId
import com.passly.domain.venue.VenueId
import com.passly.web.api.TestVenue
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
import com.passly.web.api.securedMockMvc
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
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
    lateinit var managerId: UUID
    lateinit var manager: RequestPostProcessor

    private val venueId get() = VenueId(venue.venueId)
    private val base get() = "/admin/venues/${venue.venueId}/staff"
    private val unique = UUID.randomUUID().toString().take(8)

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        managerId = jdbc.createMember(venue, Role.MANAGER)
        manager = user(userDetailsService.loadUserByUsername("$managerId@test.passly"))
    }

    private fun email(name: String) = "$name-$unique@bistro.es"

    private fun hireLucia(role: Role = Role.WAITER) = staff.hire(
        HireStaffCommand(venueId, Email.of(email("lucia")), "Lucia", RawPassword.of("password-1"), role),
    )

    private fun roleOf(userId: UUID) = staff.list(venueId).single { it.userId == UserId(userId) }.role

    @Test
    fun `list shows the staff and marks the current user`() {
        hireLucia()

        mockMvc.get(base) { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("Lucia")) }
            content { string(containsString("(you)")) }
        }
    }

    @Test
    fun `new waiter is hired and can log in`() {
        mockMvc.post(base) {
            with(manager)
            with(csrf())
            param("displayName", "Pablo")
            param("email", email("pablo"))
            param("password", "password-1")
            param("role", "KITCHEN")
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Pablo joined as kitchen") }
        }

        val pablo = staff.list(venueId).single { it.displayName == "Pablo" }
        assertEquals(Role.KITCHEN, pablo.role)
        userDetailsService.loadUserByUsername(email("pablo"))
    }

    @Test
    fun `invalid email and short password are shown next to the fields`() {
        mockMvc.post(base) {
            with(manager)
            with(csrf())
            param("displayName", "Pablo")
            param("email", "pablo")
            param("password", "short")
            param("role", "WAITER")
        }.andExpect {
            status { isOk() }
            view { name("admin/staff-form") }
            model { attributeHasFieldErrors("form", "email", "password") }
            content { string(not(containsString("value=\"short\""))) }
        }
    }

    @Test
    fun `email of an existing user is rejected`() {
        hireLucia()

        mockMvc.post(base) {
            with(manager)
            with(csrf())
            param("displayName", "Lucia again")
            param("email", email("lucia"))
            param("password", "password-1")
            param("role", "WAITER")
        }.andExpect {
            status { isOk() }
            model { attributeHasFieldErrors("form", "email") }
        }
    }

    @Test
    fun `role is changed`() {
        val lucia = hireLucia()

        mockMvc.post("$base/${lucia.id}/role") {
            with(manager)
            with(csrf())
            param("role", "KITCHEN")
        }.andExpect {
            redirectedUrl(base)
            flash { attribute("message", "Lucia is now kitchen") }
        }

        assertEquals(Role.KITCHEN, roleOf(lucia.id.value))
    }

    @Test
    fun `dismissed member leaves the list`() {
        val lucia = hireLucia()

        mockMvc.post("$base/${lucia.id}/dismiss") {
            with(manager)
            with(csrf())
        }.andExpect { redirectedUrl(base) }

        assertTrue(staff.list(venueId).none { it.userId == lucia.id })
    }

    @Test
    fun `manager cannot dismiss themselves or change their own role`() {
        mockMvc.post("$base/$managerId/dismiss") {
            with(manager)
            with(csrf())
        }.andExpect { flash { attribute("error", "You cannot dismiss yourself") } }

        mockMvc.post("$base/$managerId/role") {
            with(manager)
            with(csrf())
            param("role", "WAITER")
        }.andExpect { flash { attribute("error", "You cannot change your own role") } }

        assertEquals(Role.MANAGER, roleOf(managerId))
    }

    @Test
    fun `waiter cannot open the staff page`() {
        val waiterId = jdbc.createMember(venue, Role.WAITER)

        mockMvc.get(base) {
            with(user(userDetailsService.loadUserByUsername("$waiterId@test.passly")))
        }.andExpect { status { isForbidden() } }
    }
}
