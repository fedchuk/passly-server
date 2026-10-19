package com.passly.data.menu

import com.passly.data.common.BaseEntity
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "modifier_group")
class ModifierGroupEntity(
    id: UUID,

    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,

    @Column(name = "venue_id", nullable = false, updatable = false)
    val venueId: UUID,

    @Column(name = "name", nullable = false, length = 120)
    var name: String,

    @Column(name = "min_select", nullable = false)
    var minSelect: Int,

    @Column(name = "max_select", nullable = false)
    var maxSelect: Int,
) : BaseEntity(id) {

    @ElementCollection
    @CollectionTable(name = "modifier", joinColumns = [JoinColumn(name = "group_id")])
    @OrderColumn(name = "position")
    var modifiers: MutableList<ModifierEmbeddable> = mutableListOf()
}
