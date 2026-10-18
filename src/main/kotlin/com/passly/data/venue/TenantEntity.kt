package com.passly.data.venue

import com.passly.data.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "tenant")
class TenantEntity(
    id: UUID,
    @Column(name = "name", nullable = false, length = 160)
    var name: String,
) : BaseEntity(id)
