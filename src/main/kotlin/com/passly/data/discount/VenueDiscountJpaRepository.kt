package com.passly.data.discount

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface VenueDiscountJpaRepository : JpaRepository<VenueDiscountEntity, UUID> {
    fun findAllByVenueIdOrderBySortOrderAscNameAsc(venueId: UUID): List<VenueDiscountEntity>
}
