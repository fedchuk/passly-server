package com.passly.application.role

import com.passly.domain.role.*
import com.passly.domain.venue.Venue
import com.passly.domain.venue.VenueId

class FakeRoleRepository : RoleRepository {
    val stored = linkedMapOf<VenueRoleId, VenueRole>()

    override fun load(venueId: VenueId) =
        RoleList(venueId, stored.values.filter { it.venueId == venueId }.map(::copyOf))

    override fun saveAll(roles: Collection<VenueRole>) {
        roles.forEach { stored[it.id] = copyOf(it, (it.version ?: -1) + 1) }
    }

    override fun delete(role: VenueRole) {
        stored.remove(role.id)
    }

    fun presetsOf(venue: Venue): Map<RolePreset, VenueRoleId> {
        val list = load(venue.id)
        saveAll(list.addPresets(venue.tenantId))
        return RolePreset.entries.associateWith { preset -> list.roles.single { it.name == preset.roleName }.id }
    }

    private fun copyOf(r: VenueRole, version: Long = r.version ?: 0) =
        VenueRole.fromStorage(r.id, r.tenantId, r.venueId, r.name, r.permissions, r.sortOrder, version)
}
