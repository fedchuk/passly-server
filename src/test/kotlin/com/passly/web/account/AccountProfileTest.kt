package com.passly.web.account

import com.passly.web.api.StaffRole
import org.springframework.security.core.context.SecurityContext
import com.passly.TestcontainersConfiguration
import com.passly.domain.user.UserId
import com.passly.domain.user.UserRepository
import com.passly.security.PasslyUserPrincipal
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
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
class AccountProfileTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var users: UserRepository

    lateinit var mockMvc: MockMvc
    lateinit var managerId: UUID
    lateinit var manager: RequestPostProcessor

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        managerId = jdbc.createMember(jdbc.createVenue(), StaffRole.MANAGER)
        manager = user(userDetailsService.loadUserByUsername("$managerId@test.passly"))
    }

    @Test
    fun `profile shows the name, the email and the venues with roles`() {
        mockMvc.get("/account/profile") { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString("$managerId@test.passly")) }
            content { string(containsString("Test venue")) }
            content { string(containsString("Manager")) }
        }
    }

    @Test
    fun `account menu links to the profile`() {
        mockMvc.get("/admin") { with(manager) }.andExpect {
            content { string(containsString("href=\"/account/profile\"")) }
            content { string(containsString("Log out")) }
        }
    }

    @Test
    fun `new name is saved and the session shows it at once`() {
        val result = mockMvc.post("/account/profile") {
            with(manager)
            with(csrf())
            param("displayName", "  Maksym ")
        }.andExpect {
            redirectedUrl("/account/profile")
            flash { attribute("message", "Profile saved") }
        }.andReturn()

        assertEquals("Maksym", users.findById(UserId(managerId))!!.displayName)
        val saved = result.request.session.getAttribute(SPRING_SECURITY_CONTEXT_KEY) as SecurityContext
        val principal = saved.getAuthentication()?.principal as PasslyUserPrincipal
        assertEquals("Maksym", principal.displayName)
    }

    @Test
    fun `blank name shows the form again`() {
        mockMvc.post("/account/profile") {
            with(manager)
            with(csrf())
            param("displayName", "   ")
        }.andExpect {
            status { isOk() }
            content { string(containsString("Name must not be blank")) }
        }

        assertEquals("MANAGER", users.findById(UserId(managerId))!!.displayName)
    }

    @Test
    fun `profile needs a login`() {
        mockMvc.get("/account/profile").andExpect { status { is3xxRedirection() } }
    }
}
