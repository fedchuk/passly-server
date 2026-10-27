package com.passly.web.admin

import com.passly.application.role.GrantPermissionsCommand
import com.passly.application.role.RoleSummary
import com.passly.domain.common.ValidationException
import com.passly.domain.role.VenueRole
import com.passly.domain.role.VenueRoleId
import com.passly.domain.user.Permission
import com.passly.domain.venue.VenueId
import java.util.*

data class RoleColumnView(
    val id: UUID,
    val name: String,
    val members: Int,
    val version: Long?,
    val permissions: Set<String>,
) {
    val membersLabel: String get() = if (members == 1) "1 person" else "$members people"
}

data class RoleOptionView(
    val id: UUID,
    val name: String,
)

fun RoleSummary.toColumnView() = RoleColumnView(role.id.value, role.name, members, role.version, role.permissions.map { it.name }.toSet())

fun VenueRole.toOptionView() = RoleOptionView(id.value, name)

fun grantPermissionsCommand(venueId: VenueId, roles: List<String>, grants: List<String>): GrantPermissionsCommand {
    val versions = roles.associate { entry ->
        val (roleId, version) = entry.splitPair()
        VenueRoleId(parseUuid(roleId)) to (version.toLongOrNull() ?: throw invalid())
    }
    val granted = grants.map { entry ->
        val (roleId, permission) = entry.splitPair()
        VenueRoleId(parseUuid(roleId)) to (Permission.entries.firstOrNull { it.name == permission } ?: throw invalid())
    }.groupBy({ it.first }, { it.second })
    if (granted.keys.any { it !in versions }) throw invalid()
    return GrantPermissionsCommand(
        venueId = venueId,
        grants = versions.keys.associateWith { granted[it].orEmpty().toSet() },
        expectedVersions = versions,
    )
}

private fun String.splitPair(): Pair<String, String> {
    val parts = split(':')
    if (parts.size != 2) throw invalid()
    return parts[0] to parts[1]
}

private fun parseUuid(text: String): UUID = runCatching { UUID.fromString(text) }.getOrElse { throw invalid() }

private fun invalid() = ValidationException("The permissions form is broken. Reload the page and try again.")
