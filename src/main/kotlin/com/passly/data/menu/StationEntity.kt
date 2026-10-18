package com.passly.data.menu

import com.passly.data.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "station")
class StationEntity(
    id: UUID,

    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,

    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: UUID,

    @Column(name = "name", nullable = false, length = 80)
    var name: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
) : BaseEntity(id)
