package com.passly.data.floor

import com.passly.data.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "dining_table")
class DiningTableEntity(
    id: UUID,

    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,

    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: UUID,

    @Column(name = "area_id", nullable = false)
    var areaId: UUID,

    @Column(name = "label", nullable = false, length = 20)
    var label: String,

    @Column(name = "seats", nullable = false)
    var seats: Int,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,

    @Column(name = "active", nullable = false)
    var active: Boolean,
) : BaseEntity(id)
