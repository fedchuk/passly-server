package com.passly.data.menu

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StationJpaRepository : JpaRepository<StationEntity, UUID> {
    fun existsByIdAndVenueId(id: UUID, venueId: UUID): Boolean

    fun findAllByVenueIdOrderBySortOrderAscNameAsc(venueId: UUID): List<StationEntity>
}
