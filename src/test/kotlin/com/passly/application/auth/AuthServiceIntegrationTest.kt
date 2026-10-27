package com.passly.application.auth

import com.passly.TestcontainersConfiguration
import com.passly.application.user.HireStaffCommand
import com.passly.application.user.StaffService
import com.passly.domain.common.UnauthorizedException
import com.passly.domain.role.RolePreset
import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Email
import com.passly.domain.user.RawPassword
import com.passly.domain.venue.VenueId
import com.passly.web.api.TestVenue
import com.passly.web.api.roleId
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class AuthServiceIntegrationTest {

    @Autowired
    lateinit var auth: AuthService

    @Autowired
    lateinit var staff: StaffService

    @Autowired
    lateinit var jdbc: JdbcClient

    private val email = Email.of("anna-${UUID.randomUUID().toString().take(8)}@bistro.es")

    @BeforeEach
    fun hireWaiter() {
        val tenantId = UUID.randomUUID()
        val venueId = UUID.randomUUID()
        jdbc.sql("insert into tenant (id, name) values (?, ?)").params(tenantId, "Test tenant").update()
        jdbc.sql("insert into venue (id, tenant_id, name) values (?, ?, ?)").params(venueId, tenantId, "Test venue").update()
        staff.hire(HireStaffCommand(VenueId(venueId), email, "Anna", RawPassword.of("password-1"), VenueRoleId(jdbc.roleId(TestVenue(tenantId, venueId), RolePreset.WAITER))))
    }

    @Test
    fun `revocation after token reuse survives the failed request`() {
        val first = auth.login(LoginCommand(email, "password-1"))
        val second = auth.refresh(first.refreshToken)

        assertFailsWith<UnauthorizedException> { auth.refresh(first.refreshToken) }

        assertFailsWith<UnauthorizedException> { auth.refresh(second.refreshToken) }
    }

    @Test
    fun `database keeps only hashes of refresh tokens`() {
        val tokens = auth.login(LoginCommand(email, "password-1"))

        val leaked = jdbc.sql("select count(*) from refresh_token where token_hash = ?")
            .param(tokens.refreshToken)
            .query(Long::class.javaObjectType)
            .single()
        assertEquals(0L, leaked)
    }
}
