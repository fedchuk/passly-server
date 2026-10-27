package com.passly.data.role

import com.passly.data.common.BaseEntity
import com.passly.domain.user.Permission
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "venue_role")
class VenueRoleEntity(
    id: UUID,

    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,

    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: UUID,

    @Column(name = "name", nullable = false, length = 60)
    var name: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
) : BaseEntity(id) {

    @ElementCollection
    @CollectionTable(name = "venue_role_permission", joinColumns = [JoinColumn(name = "role_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 30)
    var permissions: MutableSet<Permission> = mutableSetOf()
}
