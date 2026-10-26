package com.passly.data.tax

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface VenueTaxRateJpaRepository : JpaRepository<VenueTaxRateEntity, UUID> {
    fun findAllByVenueIdOrderBySortOrderAscNameAsc(venueId: UUID): List<VenueTaxRateEntity>
}
