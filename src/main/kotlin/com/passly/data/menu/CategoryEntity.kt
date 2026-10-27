package com.passly.data.menu

import com.passly.data.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "category")
class CategoryEntity(
    id: UUID,

    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,

    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: UUID,

    @Column(name = "parent_id")
    var parentId: UUID?,

    @Column(name = "name", nullable = false, length = 120)
    var name: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,

    @Column(name = "station_id")
    var stationId: UUID?,

    @Column(name = "tax_rate_id")
    var taxRateId: UUID?,

    @Column(name = "active", nullable = false)
    var active: Boolean,
) : BaseEntity(id)
