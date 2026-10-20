package com.passly.web.account

import com.passly.TestcontainersConfiguration
import com.passly.domain.user.PasswordHasher
import com.passly.domain.user.Role
import com.passly.domain.user.UserId
import com.passly.domain.user.UserRepository
import com.passly.web.api.createMember
import com.passly.web.api.createVenue
import com.passly.web.api.securedMockMvc
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.web.context.WebApplicationContext
import java.util.*
import kotlin.test.Test
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class AccountPasswordTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var jdbc: JdbcClient

    @Autowired
    lateinit var userDetailsService: UserDetailsService

    @Autowired
    lateinit var users: UserRepository

    @Autowired
    lateinit var hasher: PasswordHasher

    lateinit var mockMvc: MockMvc
    lateinit var managerId: UUID
    lateinit var manager: RequestPostProcessor

    @BeforeEach
    fun setUp() {
        mockMvc = context.securedMockMvc()
        managerId = jdbc.createMember(jdbc.createVenue(), Role.MANAGER)
        manager = user(userDetailsService.loadUserByUsername("$managerId@test.passly"))
    }

    private fun submit(current: String, new: String, confirm: String = new): ResultActionsDsl =
        mockMvc.post("/account/password") {
            with(manager)
            with(csrf())
            param("currentPassword", current)
            param("newPassword", new)
            param("confirmPassword", confirm)
        }

    @Test
    fun `password is changed`() {
        submit("unused", "brand-new-password").andExpect {
            redirectedUrl("/account/password")
            flash { attributeExists("message") }
        }

        val stored = users.findById(UserId(managerId))!!
        assertTrue(hasher.matches("brand-new-password", stored.passwordHash))
    }

    @Test
    fun `wrong current password is shown next to the field`() {
        submit("not-my-password", "brand-new-password").andExpect {
            status { isOk() }
            model { attributeHasFieldErrors("form", "currentPassword") }
        }
    }

    @Test
    fun `new password must be repeated exactly`() {
        submit("unused", "brand-new-password", "brand-new-pasword").andExpect {
            status { isOk() }
            model { attributeHasFieldErrors("form", "confirmPassword") }
        }
    }

    @Test
    fun `short new password is rejected`() {
        submit("unused", "short").andExpect {
            status { isOk() }
            model { attributeHasFieldErrors("form", "newPassword") }
        }
    }

    @Test
    fun `anonymous visitor is sent to the login page`() {
        mockMvc.get("/account/password").andExpect { redirectedUrl("/login") }
    }
}
