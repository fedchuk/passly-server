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
    lateinit var ownerId: UUID
    lateinit var owner: RequestPostProcessor

    private val venueId get() = VenueId(venue.venueId)
    private val base get() = "/admin/venues/${venue.venueId}/staff"
    private val unique = UUID.randomUUID().toString().take(8)

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
        ownerId = jdbc.createMember(venue, Role.OWNER)
        owner = loggedIn(ownerId)
    }

    private fun loggedIn(userId: UUID) = user(userDetailsService.loadUserByUsername("$userId@test.passly"))

    private fun email(name: String) = "$name-$unique@bistro.es"

    private fun hireLucia(role: Role = Role.WAITER) = staff.hire(
        HireStaffCommand(venueId, Email.of(email("lucia")), "Lucia", RawPassword.of("password-1"), role),
    )

    private fun roleOf(userId: UUID) = staff.list(venueId).single { it.userId == UserId(userId) }.role

    @Test
    fun `owner sees the staff`() {
        hireLucia()

        mockMvc.get(base) { with(owner) }.andExpect {
            status { isOk() }
            content { string(containsString("Lucia")) }
        }
    }

    @Test
    fun `new waiter is hired and can log in`() {
        mockMvc.post(base) {
            with(owner)
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
            with(owner)
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
            with(owner)
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
            with(owner)
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
            with(owner)
            with(csrf())
        }.andExpect { redirectedUrl(base) }

        assertTrue(staff.list(venueId).none { it.userId == lucia.id })
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
            param("role", "WAITER")
        }.andExpect { flash { attribute("error", "You cannot change your own role") } }
    }

    @Test
    fun `manager and waiter cannot open the staff page`() {
        val manager = jdbc.createMember(venue, Role.MANAGER)
        val waiter = jdbc.createMember(venue, Role.WAITER)

        mockMvc.get(base) { with(loggedIn(manager)) }.andExpect { status { isForbidden() } }
        mockMvc.get(base) { with(loggedIn(waiter)) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `manager cannot hire`() {
        val manager = jdbc.createMember(venue, Role.MANAGER)

        mockMvc.post(base) {
            with(loggedIn(manager))
            with(csrf())
            param("displayName", "Pablo")
            param("email", email("pablo"))
            param("password", "password-1")
            param("role", "WAITER")
        }.andExpect { status { isForbidden() } }

        assertTrue(staff.list(venueId).none { it.displayName == "Pablo" })
    }
}
