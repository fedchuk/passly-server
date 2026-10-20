package com.passly.web.api

import com.passly.domain.user.Role
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
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

fun JdbcClient.createMember(venue: TestVenue, role: Role): UUID {
    val userId = UUID.randomUUID()
    sql("insert into app_user (id, email, display_name, password_hash) values (?, ?, ?, ?)")
        .params(userId, "$userId@test.passly", role.name, "{noop}unused")
        .update()
    if (role == Role.OWNER) {
        sql("insert into membership (user_id, position, tenant_id, role) values (?, 0, ?, ?)")
            .params(userId, venue.tenantId, role.name)
            .update()
    } else {
        sql("insert into membership (user_id, position, tenant_id, venue_id, role) values (?, 0, ?, ?, ?)")
            .params(userId, venue.tenantId, venue.venueId, role.name)
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
        .addFilters<DefaultMockMvcBuilder>(CharacterEncodingFilter(Charsets.UTF_8.name(), true))
        .apply<DefaultMockMvcBuilder>(springSecurity())
