package com.passly.data.menu

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ModifierGroupJpaRepository : JpaRepository<ModifierGroupEntity, UUID> {

    @EntityGraph(attributePaths = ["modifiers"])
    fun findAllByVenueId(venueId: UUID): List<ModifierGroupEntity>

    @EntityGraph(attributePaths = ["modifiers"])
    fun findByIdAndVenueId(id: UUID, venueId: UUID): ModifierGroupEntity?
}
