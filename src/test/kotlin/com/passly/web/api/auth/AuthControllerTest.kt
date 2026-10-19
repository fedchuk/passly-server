package com.passly.web.api.auth

import com.jayway.jsonpath.JsonPath
import com.passly.TestcontainersConfiguration
import com.passly.application.user.HireStaffCommand
import com.passly.application.user.StaffService
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.user.Role
import com.passly.domain.venue.VenueId
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var staff: StaffService

    @Autowired
    lateinit var jdbc: JdbcClient

    private val email = "anna-${UUID.randomUUID().toString().take(8)}@bistro.es"

    @BeforeEach
    fun hireWaiter() {
        val tenantId = UUID.randomUUID()
        val venueId = UUID.randomUUID()
        jdbc.sql("insert into tenant (id, name) values (?, ?)").params(tenantId, "Test tenant").update()
        jdbc.sql("insert into venue (id, tenant_id, name) values (?, ?, ?)").params(venueId, tenantId, "Test venue").update()
        staff.hire(
            HireStaffCommand(
                VenueId(venueId),
                Email.of(email),
                "Anna",
                RawPassword.of("password-1"),
                Role.WAITER
            )
        )
    }

    private fun login(password: String = "password-1") = mockMvc.post("/api/v1/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"email": "$email", "password": "$password"}"""
    }

    private fun refresh(refreshToken: String) = mockMvc.post("/api/v1/auth/refresh") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"refreshToken": "$refreshToken"}"""
    }

    private fun ResultActionsDsl.read(path: String): String =
        JsonPath.read(andReturn().response.contentAsString, path)

    @Test
    fun `access token from login opens the profile`() {
        val accessToken = login()
            .andExpect {
                status { isOk() }
                jsonPath("$.tokenType") { value("Bearer") }
                jsonPath("$.refreshToken") { exists() }
            }
            .read("$.accessToken")

        mockMvc.get("/api/v1/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value(email) }
            jsonPath("$.memberships[0].role") { value("WAITER") }
        }
    }

    @Test
    fun `profile needs a valid token`() {
        mockMvc.get("/api/v1/me").andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `wrong password gets a problem detail`() {
        login("wrong-pass").andExpect {
            status { isUnauthorized() }
            jsonPath("$.detail") { value("Invalid email or password") }
        }
    }

    @Test
    fun `refresh token works only once`() {
        val refreshToken = login().read("$.refreshToken")

        refresh(refreshToken).andExpect { status { isOk() } }
        refresh(refreshToken).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `everything outside the api is closed`() {
        mockMvc.get("/something-else").andExpect { status { isForbidden() } }
    }
}
