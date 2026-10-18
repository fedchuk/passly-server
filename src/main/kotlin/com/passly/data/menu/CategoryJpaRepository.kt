package com.passly.data.menu

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CategoryJpaRepository : JpaRepository<CategoryEntity, UUID> {
    fun findAllByVenueId(venueId: UUID): List<CategoryEntity>
}
