package com.passly.web

import com.passly.web.api.doesNotContain
import com.passly.TestcontainersConfiguration
import com.passly.domain.user.Role
import com.passly.web.api.TestVenue
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class WorkspaceTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    lateinit var mockMvc: MockMvc
    lateinit var venue: TestVenue

    private val service get() = "/service/venues/${venue.venueId}"
    private val kitchen get() = "/kitchen/venues/${venue.venueId}"
    private val backOffice get() = "/admin/venues/${venue.venueId}/overview"

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        venue = jdbc.createVenue()
    }

    private fun loggedIn(userId: UUID) = user(userDetailsService.loadUserByUsername("$userId@test.passly"))

    private fun member(role: Role, at: TestVenue = venue) = loggedIn(jdbc.createMember(at, role))

    @Test
    fun `venue card leads every role to its workspace`() {
        mockMvc.get("/admin") { with(member(Role.MANAGER)) }.andExpect { content { string(containsString(backOffice)) } }
        mockMvc.get("/admin") { with(member(Role.WAITER)) }.andExpect { content { string(containsString(service)) } }
        mockMvc.get("/admin") { with(member(Role.KITCHEN)) }.andExpect { content { string(containsString(kitchen)) } }
    }

    @Test
    fun `waiter works on the floor only`() {
        val waiter = member(Role.WAITER)

        mockMvc.get(service) { with(waiter) }.andExpect {
            status { isOk() }
            view { name("service/floor") }
            content { string(doesNotContain(backOffice)) }
        }
        mockMvc.get(kitchen) { with(waiter) }.andExpect { status { isForbidden() } }
        mockMvc.get(backOffice) { with(waiter) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `kitchen works on the board only`() {
        val cook = member(Role.KITCHEN)

        mockMvc.get(kitchen) { with(cook) }.andExpect {
            status { isOk() }
            view { name("kitchen/board") }
        }
        mockMvc.get(service) { with(cook) }.andExpect { status { isForbidden() } }
        mockMvc.get(backOffice) { with(cook) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `manager moves between the back office and both workspaces`() {
        val manager = member(Role.MANAGER)

        mockMvc.get(backOffice) { with(manager) }.andExpect {
            content { string(containsString(service)) }
            content { string(containsString(kitchen)) }
        }
        mockMvc.get(service) { with(manager) }.andExpect {
            status { isOk() }
            content { string(containsString(backOffice)) }
        }
        mockMvc.get(kitchen) { with(manager) }.andExpect { status { isOk() } }
    }

    @Test
    fun `staff of another venue cannot open the workspace`() {
        val stranger = member(Role.WAITER, at = jdbc.createVenue())

        mockMvc.get(service) { with(stranger) }.andExpect { status { isForbidden() } }
    }
}
