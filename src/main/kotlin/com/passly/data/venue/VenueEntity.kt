package com.passly.data.venue

import com.passly.data.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

// restaurant
@Entity
@Table(name = "venue")
class VenueEntity(
    id: UUID,

    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,

    @Column(name = "name", nullable = false, length = 160)
    var name: String,
) : BaseEntity(id) {
    @Column(name = "menu_version", nullable = false, insertable = false, updatable = false)
    var menuVersion: Long = 0
        protected set
}
