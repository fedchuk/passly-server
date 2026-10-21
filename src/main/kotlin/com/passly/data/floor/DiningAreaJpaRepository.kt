package com.passly.data.floor

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface DiningAreaJpaRepository : JpaRepository<DiningAreaEntity, UUID> {
    fun findAllByVenueId(venueId: UUID): List<DiningAreaEntity>
}
