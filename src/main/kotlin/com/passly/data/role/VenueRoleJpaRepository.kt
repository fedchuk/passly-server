package com.passly.data.role

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface VenueRoleJpaRepository : JpaRepository<VenueRoleEntity, UUID> {

    @EntityGraph(attributePaths = ["permissions"])
    fun findAllByVenueIdOrderBySortOrderAscNameAsc(venueId: UUID): List<VenueRoleEntity>
}
