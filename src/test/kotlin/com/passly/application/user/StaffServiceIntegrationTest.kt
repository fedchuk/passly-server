package com.passly.application.user

import com.passly.TestcontainersConfiguration
import com.passly.domain.common.ConflictException
import com.passly.domain.role.RolePreset
import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Email
import com.passly.domain.user.PasswordHasher
import com.passly.domain.user.RawPassword
import com.passly.domain.user.UserRepository
import com.passly.domain.venue.VenueId
import com.passly.web.api.TestVenue
import com.passly.web.api.createVenue
import com.passly.web.api.roleId
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.*
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

    lateinit var venue: TestVenue
    private val venueId get() = VenueId(venue.venueId)
    private val unique = UUID.randomUUID().toString().take(8)

    @BeforeEach
    fun createVenue() {
        venue = jdbc.createVenue()
    }

    private fun email(name: String) = Email.of("$name-$unique@bistro.es")

    private fun role(preset: RolePreset) = VenueRoleId(jdbc.roleId(venue, preset))

    private fun hire(name: String, preset: RolePreset = RolePreset.WAITER) =
        service.hire(HireStaffCommand(venueId, email(name), name, RawPassword.of("password-1"), role(preset)))

    @Test
    fun `hired staff member survives a round trip with a real password hash`() {
        val anna = hire("Anna", RolePreset.MANAGER)

        val stored = users.findById(anna.userId)!!

        assertEquals(anna.email, stored.email)
        assertTrue(stored.passwordHash.startsWith("{"))
        assertTrue(passwordHasher.matches("password-1", stored.passwordHash))
        assertEquals(listOf("Manager"), service.list(venueId).map { it.roleName })
    }

    @Test
    fun `changing a role is persisted and bumps the version`() {
        val anna = hire("Anna")

        service.changeRole(ChangeStaffRoleCommand(venueId, anna.userId, role(RolePreset.KITCHEN)))

        val stored = users.findById(anna.userId)!!
        assertEquals(listOf(role(RolePreset.KITCHEN)), stored.memberships.map { it.roleId })
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
