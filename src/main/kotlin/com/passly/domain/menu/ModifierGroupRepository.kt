package com.passly.domain.menu

import com.passly.domain.venue.VenueId

interface ModifierGroupRepository {
    fun findAll(venueId: VenueId): List<ModifierGroup>
    fun find(venueId: VenueId, id: ModifierGroupId): ModifierGroup?
    fun save(group: ModifierGroup)
    fun delete(group: ModifierGroup)
}
