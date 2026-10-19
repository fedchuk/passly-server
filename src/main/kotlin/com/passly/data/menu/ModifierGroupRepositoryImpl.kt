package com.passly.data.menu

import com.passly.data.common.upsert
import com.passly.domain.menu.ModifierGroup
import com.passly.domain.menu.ModifierGroupId
import com.passly.domain.menu.ModifierGroupRepository
import com.passly.domain.venue.VenueId
import org.springframework.stereotype.Repository

@Repository
class ModifierGroupRepositoryImpl(
    private val jpa: ModifierGroupJpaRepository,
) : ModifierGroupRepository {

    override fun findAll(venueId: VenueId): List<ModifierGroup> =
        jpa.findAllByVenueId(venueId.value).map { it.toDomain() }

    override fun find(venueId: VenueId, id: ModifierGroupId): ModifierGroup? =
        jpa.findByIdAndVenueId(id.value, venueId.value)?.toDomain()

    override fun save(group: ModifierGroup) {
        jpa.upsert(group.id.value, group.version, create = { group.toNewEntity() }, update = { updateFrom(group) })
    }

    override fun delete(group: ModifierGroup) {
        jpa.deleteById(group.id.value)
    }
}
