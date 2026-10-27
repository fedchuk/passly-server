package com.passly.domain.role

import com.passly.domain.venue.VenueId

interface RoleRepository {
    fun load(venueId: VenueId): RoleList
    fun saveAll(roles: Collection<VenueRole>)
    fun delete(role: VenueRole)
}
