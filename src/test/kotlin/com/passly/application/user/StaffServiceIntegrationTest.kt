package com.passly.application.user

import com.passly.TestcontainersConfiguration
import com.passly.domain.common.ConflictException
import com.passly.domain.user.Email
import com.passly.domain.user.PasswordHasher
import com.passly.domain.user.RawPassword
import com.passly.domain.user.Role
import com.passly.domain.user.UserRepository
import com.passly.domain.venue.VenueId
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class StaffServiceIntegrationTest {

    @Autowired
    lateinit var service: StaffService

    @Autowired
    lateinit var users: UserRepository

    @Autowired
    lateinit var passwordHasher: PasswordHasher

    @Autowired
    lateinit var jdbc: JdbcClient

    private val venueId = VenueId(UUID.randomUUID())
    private val unique = UUID.randomUUID().toString().take(8)

    @BeforeEach
    fun createVenue() {
        val tenantId = UUID.randomUUID()
        jdbc.sql("insert into tenant (id, name) values (?, ?)").params(tenantId, "Test tenant").update()
        jdbc.sql("insert into venue (id, tenant_id, name) values (?, ?, ?)")
            .params(venueId.value, tenantId, "Test venue")
            .update()
    }

    private fun email(name: String) = Email.of("$name-$unique@bistro.es")

    private fun hire(name: String, role: Role = Role.WAITER) =
        service.hire(HireStaffCommand(venueId, email(name), name, RawPassword.of("password-1"), role))

    @Test
    fun `hired staff member survives a round trip with a real password hash`() {
        val anna = hire("Anna", Role.MANAGER)

        val stored = users.findById(anna.id)!!

        assertEquals(anna.email, stored.email)
        assertTrue(stored.passwordHash.startsWith("{"))
        assertTrue(passwordHasher.matches("password-1", stored.passwordHash))
        assertEquals(listOf(Role.MANAGER), service.list(venueId).map { it.role })
    }

    @Test
    fun `changing a role is persisted and bumps the version`() {
        val anna = hire("Anna")

        service.changeRole(ChangeStaffRoleCommand(venueId, anna.id, Role.KITCHEN))

        val stored = users.findById(anna.id)!!
        assertEquals(listOf(Role.KITCHEN), stored.memberships.map { it.role })
        assertEquals(1L, stored.version)
    }

    @Test
    fun `email is unique regardless of case`() {
        hire("Anna")

        assertFailsWith<ConflictException> { hire("Anna") }
        assertFailsWith<DataIntegrityViolationException> {
            jdbc.sql("insert into app_user (id, email, display_name, password_hash) values (?, ?, ?, ?)")
                .params(UUID.randomUUID(), email("Anna").value.uppercase(), "Copy", "x")
                .update()
        }
    }
}
