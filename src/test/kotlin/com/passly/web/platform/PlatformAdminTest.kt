package com.passly.web.platform

import com.passly.TestcontainersConfiguration
import com.passly.application.platform.BusinessDirectoryQuery
import com.passly.application.platform.PlatformAdminService
import com.passly.application.platform.RegisterPlatformAdminCommand
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.Role
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
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

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PlatformAdminTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var platformAdmins: PlatformAdminService

    @Autowired
    lateinit var directory: BusinessDirectoryQuery

    lateinit var mockMvc: MockMvc
    lateinit var admin: RequestPostProcessor

    private val unique = UUID.randomUUID().toString().take(8)

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        val email = "admin-$unique@passly.dev"
        platformAdmins.register(RegisterPlatformAdminCommand(Email.of(email), "Admin", RawPassword.of("password-1")))
        admin = user(userDetailsService.loadUserByUsername(email))
    }

    private fun registerBusiness(name: String, ownerEmail: String) = mockMvc.post("/platform/businesses") {
        with(admin)
        with(csrf())
        param("businessName", name)
        param("venueName", "Centro")
        param("ownerName", "Max")
        param("ownerEmail", ownerEmail)
        param("ownerPassword", "password-1")
    }

    @Test
    fun `admin creates a business and its owner can log in`() {
        val ownerEmail = "owner-$unique@bistro.es"

        registerBusiness("Bistro $unique", ownerEmail).andExpect {
            redirectedUrl("/platform")
            flash { attribute("message", "Bistro $unique is ready. Max can log in as $ownerEmail") }
        }

        val business = directory.allBusinesses().single { it.name == "Bistro $unique" }
        assertEquals(1, business.venues)
        assertEquals(ownerEmail, business.owners)
        userDetailsService.loadUserByUsername(ownerEmail)
    }

    @Test
    fun `businesses page lists every business`() {
        registerBusiness("Tapas $unique", "tapas-$unique@bistro.es")

        mockMvc.get("/platform") { with(admin) }.andExpect {
            status { isOk() }
            content { string(containsString("Tapas $unique")) }
            content { string(containsString("tapas-$unique@bistro.es")) }
        }
    }

    @Test
    fun `taken owner email is shown next to the field`() {
        registerBusiness("First $unique", "taken-$unique@bistro.es")

        registerBusiness("Second $unique", "taken-$unique@bistro.es").andExpect {
            status { isOk() }
            view { name("platform/business-form") }
            model { attributeHasFieldErrors("form", "ownerEmail") }
        }
        assertEquals(0, directory.allBusinesses().count { it.name == "Second $unique" })
    }

    @Test
    fun `platform admin lands on the platform after login`() {
        mockMvc.get("/admin") { with(admin) }.andExpect { redirectedUrl("/platform") }
    }

    @Test
    fun `venue owner cannot open the platform`() {
        val venue = jdbc.createVenue()
        val ownerId = jdbc.createMember(venue, Role.OWNER)

        mockMvc.get("/platform") {
            with(user(userDetailsService.loadUserByUsername("$ownerId@test.passly")))
        }.andExpect { status { isForbidden() } }
    }
}
