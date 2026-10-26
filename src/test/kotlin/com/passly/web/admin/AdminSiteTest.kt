    package com.passly.web.admin

    import com.passly.TestcontainersConfiguration
import com.passly.application.menu.CategoryService
import com.passly.application.menu.CreateCategoryCommand
import com.passly.domain.menu.StationId
import com.passly.domain.user.Role
import com.passly.domain.venue.VenueId
import com.passly.web.api.*
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test

    @SpringBootTest
    @Import(TestcontainersConfiguration::class)
    class AdminSiteTest {

        @Autowired
        lateinit var context: WebApplicationContext

        @Autowired
        lateinit var jdbc: JdbcClient

        @Autowired
        lateinit var userDetailsService: UserDetailsService

        @Autowired
        lateinit var categories: CategoryService

        lateinit var mockMvc: MockMvc
        lateinit var venue: TestVenue

        @BeforeEach
        fun setUp() {
            mockMvc = context.securedMockMvc()
            venue = jdbc.createVenue()
        }

        private fun emailOf(userId: UUID) = "$userId@test.passly"

        private fun loggedIn(userId: UUID) = user(userDetailsService.loadUserByUsername(emailOf(userId)))

        @Test
        fun `anonymous visitor is sent to the login page`() {
            mockMvc.get("/admin").andExpect {
                status { is3xxRedirection() }
                redirectedUrl("/login")
            }
        }

        @Test
        fun `login page is open`() {
            mockMvc.get("/login").andExpect {
                status { isOk() }
                content { string(containsString("name=\"_csrf\"")) }
            }
        }

        @Test
        fun `login form without a csrf token is rejected`() {
            val manager = jdbc.createMember(venue, Role.MANAGER)

            mockMvc.post("/login") {
                param("username", emailOf(manager))
                param("password", "unused")
            }.andExpect { status { isForbidden() } }
        }

        @Test
        fun `manager logs in and lands on the dashboard`() {
            val manager = jdbc.createMember(venue, Role.MANAGER)

            mockMvc.perform(formLogin().user(emailOf(manager)).password("unused"))
                .andExpect(redirectedUrl("/admin"))
                .andExpect(authenticated())
        }

        @Test
        fun `wrong password returns to the login page with an error`() {
            val manager = jdbc.createMember(venue, Role.MANAGER)

            mockMvc.perform(formLogin().user(emailOf(manager)).password("wrong"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated())
        }

        @Test
        fun `dashboard lists the venues of the user`() {
            val owner = jdbc.createMember(venue, Role.OWNER)

            mockMvc.get("/admin") { with(loggedIn(owner)) }.andExpect {
                status { isOk() }
                content { string(containsString("Test venue")) }
                content { string(containsString("/admin/venues/${venue.venueId}")) }
            }
        }

        @Test
        fun `venue page shows the category tree`() {
            val manager = jdbc.createMember(venue, Role.MANAGER)
            val wine = categories.create(CreateCategoryCommand(VenueId(venue.venueId), "Wine", stationId = StationId(jdbc.createStation(venue))))
            categories.create(CreateCategoryCommand(VenueId(venue.venueId), "Rioja", parentId = wine.id))

            mockMvc.get("/admin/venues/${venue.venueId}/categories/${wine.id.value}/items") { with(loggedIn(manager)) }.andExpect {
                status { isOk() }
                content { string(containsString("Wine")) }
                content { string(containsString("Rioja")) }
            }
        }

        @Test
        fun `waiter cannot open the venue admin`() {
            val waiter = jdbc.createMember(venue, Role.WAITER)

            mockMvc.get("/admin/venues/${venue.venueId}") { with(loggedIn(waiter)) }.andExpect {
                status { isForbidden() }
            }
        }

        @Test
        fun `unknown pages stay closed after login`() {
            val owner = jdbc.createMember(venue, Role.OWNER)

            mockMvc.get("/something-else") { with(loggedIn(owner)) }.andExpect { status { isForbidden() } }
        }

        @Test
        fun `remember me sets a long lived cookie`() {
            val manager = jdbc.createMember(venue, Role.MANAGER)

            mockMvc.post("/login") {
                with(csrf())
                param("username", emailOf(manager))
                param("password", "unused")
                param("remember-me", "on")
            }.andExpect {
                redirectedUrl("/admin")
                cookie {
                    exists("remember-me")
                    maxAge("remember-me", 14 * 24 * 60 * 60)
                    httpOnly("remember-me", true)
                }
            }
        }

        @Test
        fun `without remember me there is no cookie`() {
            val manager = jdbc.createMember(venue, Role.MANAGER)

            mockMvc.post("/login") {
                with(csrf())
                param("username", emailOf(manager))
                param("password", "unused")
            }.andExpect {
                redirectedUrl("/admin")
                cookie { doesNotExist("remember-me") }
            }
        }

        @Test
        fun `venue pages show the venue menu in the sidebar`() {
            val manager = jdbc.createMember(venue, Role.MANAGER)

            mockMvc.get("/admin/venues/${venue.venueId}") { with(loggedIn(manager)) }.andExpect {
                status { isOk() }
                content { string(containsString("aria-label=\"Venue\"")) }
                content { string(containsString("Modifier groups")) }
            }
        }
    }
