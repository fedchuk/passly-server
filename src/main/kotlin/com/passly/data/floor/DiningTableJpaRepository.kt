package com.passly.data.floor

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface DiningTableJpaRepository : JpaRepository<DiningTableEntity, UUID> {
    fun findAllByVenueId(venueId: UUID): List<DiningTableEntity>
}
