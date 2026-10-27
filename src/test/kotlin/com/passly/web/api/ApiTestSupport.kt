package com.passly.web.api

import com.passly.domain.role.RolePreset
import com.passly.domain.user.Permission
import org.hamcrest.Matcher
import org.hamcrest.CoreMatchers
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.filter.CharacterEncodingFilter
import java.util.UUID

data class TestVenue(val tenantId: UUID, val venueId: UUID)

fun JdbcClient.createVenue(): TestVenue {
    val venue = TestVenue(UUID.randomUUID(), UUID.randomUUID())
    sql("insert into tenant (id, name) values (?, ?)").params(venue.tenantId, "Test tenant").update()
    sql("insert into venue (id, tenant_id, name) values (?, ?, ?)").params(venue.venueId, venue.tenantId, "Test venue").update()
    return venue
}

fun JdbcClient.createStation(venue: TestVenue, name: String = "Test kitchen"): UUID {
    val id = UUID.randomUUID()
    sql("insert into station (id, tenant_id, venue_id, name) values (?, ?, ?, ?)")
        .params(id, venue.tenantId, venue.venueId, name)
        .update()
    return id
}

fun JdbcClient.roleId(venue: TestVenue, preset: RolePreset): UUID {
    sql("insert into venue_role (id, tenant_id, venue_id, name, sort_order) values (?, ?, ?, ?, ?) on conflict do nothing")
        .params(UUID.randomUUID(), venue.tenantId, venue.venueId, preset.roleName, preset.ordinal)
        .update()
    val roleId = sql("select id from venue_role where venue_id = ? and name = ?")
        .params(venue.venueId, preset.roleName)
        .query(UUID::class.java)
        .single()
    (preset.permissions + Permission.EVERY_ROLE).forEach { permission ->
        sql("insert into venue_role_permission (role_id, permission) values (?, ?) on conflict do nothing")
            .params(roleId, permission.name)
            .update()
    }
    return roleId
}

fun JdbcClient.createMember(venue: TestVenue, role: StaffRole): UUID {
    val userId = UUID.randomUUID()
    sql("insert into app_user (id, email, display_name, password_hash) values (?, ?, ?, ?)")
        .params(userId, "$userId@test.passly", role.name, "{noop}unused")
        .update()
    val preset = role.preset
    if (preset == null) {
        sql("insert into membership (user_id, position, tenant_id) values (?, 0, ?)")
            .params(userId, venue.tenantId)
            .update()
    } else {
        sql("insert into membership (user_id, position, tenant_id, venue_id, role_id) values (?, 0, ?, ?, ?)")
            .params(userId, venue.tenantId, venue.venueId, roleId(venue, preset))
            .update()
    }
    return userId
}

fun asUser(userId: UUID): RequestPostProcessor = jwt().jwt { it.subject(userId.toString()) }

fun WebApplicationContext.securedMockMvc(): MockMvc =
    builder().build()

fun WebApplicationContext.mockMvcAs(userId: UUID): MockMvc =
    builder().defaultRequest<DefaultMockMvcBuilder>(MockMvcRequestBuilders.get("/").with(asUser(userId))).build()

private fun WebApplicationContext.builder(): DefaultMockMvcBuilder =
    MockMvcBuilders.webAppContextSetup(this)
        .addFilters<DefaultMockMvcBuilder>(CharacterEncodingFilter(Charsets.UTF_8.name(), true, false))
        .apply<DefaultMockMvcBuilder>(springSecurity())

fun doesNotContain(text: String): Matcher<String> = CoreMatchers.not(CoreMatchers.containsString(text))
