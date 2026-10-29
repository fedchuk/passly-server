package com.passly.data.discount

import com.passly.data.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.*

@Entity
@Table(name = "venue_discount")
class VenueDiscountEntity(
    id: UUID,

    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,

    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: UUID,

    @Column(name = "name", nullable = false, length = 80)
    var name: String,

    @Column(name = "percent", nullable = false, precision = 5, scale = 2)
    var percent: BigDecimal,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
) : BaseEntity(id)
